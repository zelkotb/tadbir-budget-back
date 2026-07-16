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
package ma.zakaria.tadbirbudget.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.auth.dto.AuthRequestContext;
import ma.zakaria.tadbirbudget.auth.dto.LoginInput;
import ma.zakaria.tadbirbudget.auth.dto.RefreshOutput;
import ma.zakaria.tadbirbudget.auth.dto.SignupInput;
import ma.zakaria.tadbirbudget.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (public). Request/response logging is handled centrally by
 * {@code ControllerLoggingAspect}, which masks credentials and tokens.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<RefreshOutput> signup(
            @Valid @RequestBody SignupInput input,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.signup(input, response, AuthRequestContext.from(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<RefreshOutput> login(
            @Valid @RequestBody LoginInput input,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(input, response, AuthRequestContext.from(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshOutput> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.refresh(request, response, AuthRequestContext.from(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response, AuthRequestContext.from(request));
        return ResponseEntity.ok().build();
    }
}