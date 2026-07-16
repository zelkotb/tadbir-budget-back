/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.auth.dto.AuthRequestContext;
import ma.zakaria.tadbirbudget.auth.dto.LoginInput;
import ma.zakaria.tadbirbudget.auth.dto.RefreshOutput;
import ma.zakaria.tadbirbudget.auth.dto.SignupInput;
import ma.zakaria.tadbirbudget.entity.ChangeFreeze;
import ma.zakaria.tadbirbudget.entity.RefreshToken;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.enums.AuthEventType;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.util.MdcKeys;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.repository.ChangeFreezeRepository;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import ma.zakaria.tadbirbudget.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.MDC;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository         userRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;
    private final RefreshTokenService    refreshTokenService;
    private final AuthenticationManager  authenticationManager;
    private final AuthAuditService       authAuditService;
    private final ChangeFreezeRepository changeFreezeRepository;

    @Value("${refresh.expiration}")
    private long refreshExpirationSeconds;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${lockout.max-attempts:5}")
    private int lockoutMaxAttempts;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional
    public RefreshOutput signup(SignupInput input, HttpServletResponse response, AuthRequestContext ctx) {
        // Global change-freeze: while frozen, no new citizen accounts may be created.
        if (changeFreezeRepository.findById(ChangeFreeze.SINGLETON_ID)
                .map(ChangeFreeze::isFrozen).orElse(false)) {
            throw new CustomException(ErrorCode.CHANGES_FROZEN, HttpStatus.FORBIDDEN);
        }
        if (userRepository.existsByEmail(input.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
        }

        // Set the actor email in MDC before saving so CustomRevisionListener can
        // record it in revinfo — SecurityContextHolder is empty at signup time.
        // MdcFilter clears MDC after the full request (post transaction commit).
        MDC.put(MdcKeys.REVISION_ACTOR_EMAIL, input.getEmail());

        User user = User.builder()
                .fullName(input.getFullName())
                .cin(input.getCin())
                .phoneNumber(input.getPhoneNumber())
                .email(input.getEmail())
                .address(input.getAdresse())
                .password(passwordEncoder.encode(input.getPassword()))
                .roles(List.of(Roles.USER))
                .build();

        userRepository.save(user);
        RefreshOutput output = issueTokensAndBuildOutput(user, response);
        authAuditService.recordSuccess(user.getEmail(), AuthEventType.LOGIN, ctx);
        return output;
    }

    // noRollbackFor ensures counter increments committed even when CustomException is thrown.
    @Transactional(noRollbackFor = CustomException.class)
    public RefreshOutput login(LoginInput input, HttpServletResponse response, AuthRequestContext ctx) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(input.getEmail(), input.getPassword()));
        } catch (DisabledException ex) {
            authAuditService.recordFailure(input.getEmail(), AuthEventType.LOGIN, ctx);
            throw new CustomException(ErrorCode.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN);
        } catch (BadCredentialsException ex) {
            incrementFailedAttempts(input.getEmail());
            authAuditService.recordFailure(input.getEmail(), AuthEventType.LOGIN, ctx);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED));

        resetFailedAttempts(user);
        RefreshOutput output = issueTokensAndBuildOutput(user, response);
        authAuditService.recordSuccess(user.getEmail(), AuthEventType.LOGIN, ctx);
        return output;
    }

    @Transactional
    public RefreshOutput refresh(HttpServletRequest request, HttpServletResponse response, AuthRequestContext ctx) {
        String oldTokenValue = extractRefreshCookie(request)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_MISSING, HttpStatus.UNAUTHORIZED));

        RefreshToken newRefreshToken;
        try {
            newRefreshToken = refreshTokenService.rotate(oldTokenValue);
        } catch (CustomException ex) {
            authAuditService.recordFailure(null, AuthEventType.TOKEN_REFRESH, ctx);
            throw ex;
        }

        setRefreshCookie(response, newRefreshToken.getToken());

        User user = userRepository.findById(newRefreshToken.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED));

        if (!user.isEnabled()) {
            authAuditService.recordFailure(user.getEmail(), AuthEventType.TOKEN_REFRESH, ctx);
            throw new CustomException(ErrorCode.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN);
        }

        authAuditService.recordSuccess(user.getEmail(), AuthEventType.TOKEN_REFRESH, ctx);
        return new RefreshOutput(jwtService.generateToken(user));
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response, AuthRequestContext ctx) {
        extractRefreshCookie(request)
                .flatMap(refreshTokenService::revoke)
                .flatMap(userRepository::findById)
                .map(User::getEmail)
                .ifPresent(email -> authAuditService.recordSuccess(email, AuthEventType.LOGOUT, ctx));
        clearRefreshCookie(response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void incrementFailedAttempts(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= lockoutMaxAttempts) {
                user.setEnabled(false);
                log.warn("Account disabled after {} failed login attempts email=[{}]",
                        lockoutMaxAttempts, email);
            }
            userRepository.save(user);
        });
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
    }

    private RefreshOutput issueTokensAndBuildOutput(User user, HttpServletResponse response) {
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user.getId());
        setRefreshCookie(response, refreshToken.getToken());
        return new RefreshOutput(accessToken);
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(refreshExpirationSeconds)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<String> extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst();
    }
}