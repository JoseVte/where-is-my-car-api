package io.josrom.whereismycar.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.service.AuthService;

import java.util.Objects;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Idempotent: creates the local user (with the default categories) the first time a valid Firebase
     * token is seen and refreshes email and name afterwards. Clients should call it right after logging in.
     */
    @GetMapping("")
    public User me(@AuthenticationPrincipal Jwt jwt) {
        return authService.syncFromToken(
                Objects.requireNonNull(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"));
    }
}
