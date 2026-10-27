package io.josrom.whereismycar.seeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import io.josrom.whereismycar.support.IntegrationTest;

class DatabaseSeederTest extends IntegrationTest {
    private static final String EMAIL = "seed@example.com";

    @Autowired
    private ConfigurableApplicationContext context;

    private UserRecord record;

    @BeforeEach
    void setUpFirebase() {
        record = mock(UserRecord.class);
        when(record.getUid()).thenReturn("seed-uid");
        when(record.getEmail()).thenReturn(EMAIL);
    }

    private DatabaseSeeder seeder(String email, String password) {
        return new DatabaseSeeder(firebaseAuth, userRepository, categoryRepository, savedLocationRepository,
                email, password, false, context);
    }

    private void run(DatabaseSeeder seeder) throws Exception {
        seeder.run(new DefaultApplicationArguments());
    }

    @Test
    void createsTheFirebaseUserAndSeedsTheDatabase() throws Exception {
        FirebaseAuthException notFound = mock(FirebaseAuthException.class);
        when(notFound.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        when(firebaseAuth.getUserByEmail(EMAIL)).thenThrow(notFound);
        when(firebaseAuth.createUser(any())).thenReturn(record);

        run(seeder(EMAIL, "secret123"));

        assertThat(userRepository.findById("seed-uid")).isPresent();
        assertThat(categoryRepository.findByUserUuid("seed-uid")).hasSize(4);
        assertThat(savedLocationRepository.findByUserUuidOrderByDateDesc("seed-uid")).hasSize(5)
                .allSatisfy(location -> assertThat(location.getCategory()).isNotNull());
    }

    @Test
    void isIdempotentAndSyncsThePasswordOfAnExistingUser() throws Exception {
        when(firebaseAuth.getUserByEmail(EMAIL)).thenReturn(record);
        when(firebaseAuth.updateUser(any())).thenReturn(record);

        run(seeder(EMAIL, "secret123"));
        run(seeder(EMAIL, "secret123"));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(categoryRepository.findByUserUuid("seed-uid")).hasSize(4);
        assertThat(savedLocationRepository.findByUserUuidOrderByDateDesc("seed-uid")).hasSize(5);
        verify(firebaseAuth, never()).createUser(any());
    }

    @Test
    void failsWithoutEmailOrPassword() {
        assertThatThrownBy(() -> run(seeder("", "secret123"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run(seeder(EMAIL, ""))).isInstanceOf(IllegalStateException.class);
    }
}
