package io.josrom.whereismycar.component;

import java.util.Map;

import com.google.firebase.FirebaseApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.josrom.whereismycar.dto.FirebaseTokens;

/**
 * Signs a user in against the Firebase Auth REST API to obtain an ID token.
 * The Admin SDK cannot do this, it can only mint custom tokens.
 */
@Component
public class FirebaseSignInClient {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseSignInClient.class);
    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={key}";

    private final RestClient restClient;
    private final String apiKey;

    public FirebaseSignInClient(@Value("${firebase.api-key}") String apiKey) {
        this.restClient = RestClient.create();
        this.apiKey = apiKey;
    }

    /** Returns null if the sign-in fails. */
    public FirebaseTokens signIn(String email, String password) {
        try {
            return restClient.post()
                    .uri(SIGN_IN_URL, apiKey)
                    .body(Map.of("email", email, "password", password, "returnSecureToken", true))
                    .retrieve()
                    .body(FirebaseTokens.class);
        } catch (Exception e) {
            logger.error("Firebase sign-in after registration failed", e);
            return null;
        }
    }
}
