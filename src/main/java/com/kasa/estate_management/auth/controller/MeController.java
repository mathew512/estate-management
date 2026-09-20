package com.kasa.estate_management.auth.controller;

import com.kasa.estate_management.auth.dto.AuthDtos.UserResponse;
import com.kasa.estate_management.auth.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return new UserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getRole(),
            user.isActive()
        );
    }
}