package io.josrom.whereismycar.dto;

import io.josrom.whereismycar.entity.User;

/**
 * `tokens` is null if the account was created but the automatic sign-in failed,
 * in which case the client should log in with Firebase as usual.
 */
public record RegisterResponse(User user, FirebaseTokens tokens) {
}
