package com.kasa.estate_management.auth.controller;

import com.kasa.estate_management.auth.dto.PasswordResetDtos.ConfirmReset;
import com.kasa.estate_management.auth.dto.PasswordResetDtos.RequestReset;
import com.kasa.estate_management.auth.service.PasswordResetService;
import com.kasa.estate_management.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final RateLimitService rateLimitService;

    public PasswordResetController(PasswordResetService passwordResetService,
                                   RateLimitService rateLimitService) {
        this.passwordResetService = passwordResetService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody RequestReset request,
                                                       HttpServletRequest httpRequest) {
        // Each request can cost an SMS, so the address is limited before any lookup happens.
        rateLimitService.checkReset(httpRequest.getRemoteAddr());

        passwordResetService.requestReset(request.phoneNumber());
        return ResponseEntity.accepted().body(Map.of(
            "message", "If that number belongs to a tenant account, a code has been sent by SMS."));
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@Valid @RequestBody ConfirmReset request,
                                       HttpServletRequest httpRequest) {
        rateLimitService.checkReset(httpRequest.getRemoteAddr());

        boolean changed = passwordResetService.confirmReset(
            request.phoneNumber(), request.code(), request.newPassword());

        if (!changed) {
            throw new IllegalArgumentException("Invalid or expired code");
        }
        return Map.of("message", "Password updated. You can now log in.");
    }
}