package io.josrom.whereismycar.controllers;

import io.josrom.whereismycar.component.RegistrationRateLimiter;
import io.josrom.whereismycar.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.josrom.whereismycar.dto.RegisterRequest;
import io.josrom.whereismycar.dto.RegisterResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RegistrationRateLimiter registrationRateLimiter;

    public AuthController(AuthService authService, RegistrationRateLimiter registrationRateLimiter) {
        this.authService = authService;
        this.registrationRateLimiter = registrationRateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        // Invalid bodies are rejected by @Valid before this point, so they do not use the limit
        registrationRateLimiter.consume(servletRequest.getRemoteAddr());

        return authService.register(request);
    }
}
