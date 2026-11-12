package com.kasa.estate_management.auth.controller;

import com.kasa.estate_management.auth.dto.AuthDtos.LoginRequest;
import com.kasa.estate_management.auth.dto.AuthDtos.LoginResponse;
import com.kasa.estate_management.auth.dto.AuthDtos.RegisterRequest;
import com.kasa.estate_management.auth.dto.AuthDtos.UserResponse;
import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.auth.service.JwtService;
import com.kasa.estate_management.auth.service.UserService;
import com.kasa.estate_management.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;

    public AuthController(UserService userService, JwtService jwtService, RateLimitService rateLimitService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(
            request.fullName(),
            request.email(),
            request.phoneNumber(),
            request.password(),
            request.role()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toUserResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();

        // Counted before the password is checked, so a refused request costs the server almost nothing.
        rateLimitService.beforeLogin(ip, request.email());

        User user = userService.authenticate(request.email(), request.password());
        rateLimitService.afterLoginSuccess(ip, request.email());

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", toUserResponse(user)));
    }

    private UserResponse toUserResponse(User user) {
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