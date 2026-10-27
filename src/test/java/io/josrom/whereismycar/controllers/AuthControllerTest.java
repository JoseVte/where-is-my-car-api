package io.josrom.whereismycar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import io.josrom.whereismycar.dto.FirebaseTokens;
import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.service.CategoryService;
import io.josrom.whereismycar.support.IntegrationTest;

class AuthControllerTest extends IntegrationTest {
    private static final String BODY = """
            {"email": "new@example.com", "password": "password123", "displayName": "New User"}""";

    @MockitoSpyBean
    private CategoryService categoryService;

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void firebaseCreatesUser(String uid, String email) throws FirebaseAuthException {
        UserRecord record = mock(UserRecord.class);
        when(record.getUid()).thenReturn(uid);
        when(record.getEmail()).thenReturn(email);
        when(firebaseAuth.createUser(any())).thenReturn(record);
    }

    private static FirebaseAuthException firebaseError(AuthErrorCode code) {
        FirebaseAuthException exception = mock(FirebaseAuthException.class);
        when(exception.getAuthErrorCode()).thenReturn(code);
        return exception;
    }

    @Test
    void createsTheUserWithDefaultCategoriesAndReturnsTokens() throws Exception {
        firebaseCreatesUser("uid-1", "new@example.com");
        when(firebaseSignInClient.signIn("new@example.com", "password123"))
                .thenReturn(new FirebaseTokens("id-token", "refresh-token", "3600"));

        register(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.uuid").value("uid-1"))
                .andExpect(jsonPath("$.user.email").value("new@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("New User"))
                .andExpect(jsonPath("$.tokens.idToken").value("id-token"))
                .andExpect(jsonPath("$.tokens.refreshToken").value("refresh-token"));

        assertThat(userRepository.findById("uid-1")).isPresent();
        assertThat(categoryRepository.findByUserUuid("uid-1"))
                .extracting(Category::getLabel).containsExactlyInAnyOrder(Category.HOME_LABEL, Category.FAVORITES_LABEL, Category.OTHERS_LABEL);
    }

    @Test
    void stillRegistersWhenTheAutomaticSignInFails() throws Exception {
        firebaseCreatesUser("uid-1", "new@example.com");
        when(firebaseSignInClient.signIn(anyString(), anyString())).thenReturn(null);

        register(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.uuid").value("uid-1"))
                .andExpect(jsonPath("$.tokens").doesNotExist());
    }

    @Test
    void rejectsAnEmailAlreadyRegisteredInFirebase() throws Exception {
        FirebaseAuthException exception = firebaseError(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        when(firebaseAuth.createUser(any())).thenThrow(exception);

        register(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Email already registered"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void reportsOtherFirebaseErrorsAsBadGateway() throws Exception {
        FirebaseAuthException exception = firebaseError(AuthErrorCode.CONFIGURATION_NOT_FOUND);
        when(firebaseAuth.createUser(any())).thenThrow(exception);

        register(BODY)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.message").value("Could not create the account"));
    }

    @Test
    void rollsBackTheDatabaseAndDeletesTheFirebaseUserWhenSavingFails() throws Exception {
        firebaseCreatesUser("uid-1", "new@example.com");
        doThrow(new IllegalStateException("boom")).when(categoryService).createDefaultCategories(any());

        register(BODY)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("Unexpected error"));

        assertThat(userRepository.findById("uid-1")).isEmpty();
        verify(firebaseAuth).deleteUser("uid-1");
        verify(firebaseSignInClient, never()).signIn(anyString(), anyString());
    }

    @Test
    void validatesTheBody() throws Exception {
        register("""
                {"email": "not-an-email", "password": "short", "displayName": "%s"}""".formatted("x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());

        verify(firebaseAuth, never()).createUser(any());
    }

    // Rate limiting (5 registrations per IP and hour by default)

    private ResultActions registerFrom(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)));
    }

    private void firebaseCreatesAUserPerCall() throws FirebaseAuthException {
        AtomicInteger counter = new AtomicInteger();
        when(firebaseAuth.createUser(any())).thenAnswer(invocation -> {
            int n = counter.incrementAndGet();
            UserRecord record = mock(UserRecord.class);
            when(record.getUid()).thenReturn("uid-" + n);
            when(record.getEmail()).thenReturn("user" + n + "@example.com");
            return record;
        });
    }

    @Test
    void limitsRegistrationsPerIp() throws Exception {
        firebaseCreatesAUserPerCall();

        for (int i = 1; i <= 5; i++) {
            registerFrom("10.0.0.1", "user" + i + "@example.com").andExpect(status().isCreated());
        }

        registerFrom("10.0.0.1", "user6@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.message").value("Too many registration attempts, try again later"));

        verify(firebaseAuth, times(5)).createUser(any());
    }

    @Test
    void otherIpsKeepTheirOwnLimit() throws Exception {
        firebaseCreatesAUserPerCall();

        for (int i = 1; i <= 5; i++) {
            registerFrom("10.0.0.1", "user" + i + "@example.com");
        }

        registerFrom("10.0.0.2", "other@example.com").andExpect(status().isCreated());
    }

    @Test
    void invalidRequestsDoNotUseTheLimit() throws Exception {
        firebaseCreatesAUserPerCall();

        for (int i = 0; i < 10; i++) {
            registerFrom("10.0.0.1", "not-an-email").andExpect(status().isBadRequest());
        }

        registerFrom("10.0.0.1", "valid@example.com").andExpect(status().isCreated());
    }

    @Test
    void rateLimitMessageIsTranslated() throws Exception {
        firebaseCreatesAUserPerCall();

        for (int i = 1; i <= 5; i++) {
            registerFrom("10.0.0.1", "user" + i + "@example.com");
        }

        mockMvc.perform(post("/api/auth/register")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1");
                            return request;
                        })
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message").value("Demasiados intentos de registro, inténtalo más tarde"));
    }
}
