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

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.RefreshToken;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${refresh.expiration}")
    private long refreshExpirationSeconds;

    @Transactional
    public RefreshToken create(UUID userId) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(refreshExpirationSeconds))
                .build();
        return refreshTokenRepository.save(token);
    }

    /**
     * Validates the old token, marks it revoked, and issues a new one.
     * Throws a {@link CustomException} if the token is missing, revoked, or expired.
     */
    @Transactional
    public RefreshToken rotate(String oldTokenValue) {
        RefreshToken old = findValid(oldTokenValue);
        old.setRevoked(true);
        refreshTokenRepository.save(old);
        return create(old.getUserId());
    }

    /**
     * Revokes a token on logout.
     *
     * @return the userId bound to the token, or empty if the token was not found
     */
    @Transactional
    public Optional<UUID> revoke(String tokenValue) {
        return refreshTokenRepository.findByToken(tokenValue).map(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
            return t.getUserId();
        });
    }

    // ── private ──────────────────────────────────────────────────────────────

    private RefreshToken findValid(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED));

        if (token.isRevoked()) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REVOKED, HttpStatus.UNAUTHORIZED);
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED);
        }
        return token;
    }
}