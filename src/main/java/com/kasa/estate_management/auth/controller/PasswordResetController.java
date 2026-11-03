package com.kasa.estate_management.auth.controller;

import com.kasa.estate_management.auth.dto.PasswordResetDtos.ConfirmReset;
import com.kasa.estate_management.auth.dto.PasswordResetDtos.RequestReset;
import com.kasa.estate_management.auth.service.PasswordResetService;
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

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody RequestReset request) {
        passwordResetService.requestReset(request.phoneNumber());
        return ResponseEntity.accepted().body(Map.of(
            "message", "If that number belongs to a tenant account, a code has been sent by SMS."));
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@Valid @RequestBody ConfirmReset request) {
        boolean changed = passwordResetService.confirmReset(
            request.phoneNumber(), request.code(), request.newPassword());

        if (!changed) {
            throw new IllegalArgumentException("Invalid or expired code");
        }
        return Map.of("message", "Password updated. You can now log in.");
    }
}