package io.josrom.whereismycar.service;

import java.util.Locale;

import io.josrom.whereismycar.component.FirebaseSignInClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import io.josrom.whereismycar.dto.FirebaseTokens;
import io.josrom.whereismycar.dto.RegisterRequest;
import io.josrom.whereismycar.dto.RegisterResponse;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.exception.ConflictException;
import io.josrom.whereismycar.exception.ExternalServiceException;
import io.josrom.whereismycar.repository.UserRepository;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final FirebaseSignInClient firebaseSignInClient;
    private final TransactionTemplate transactionTemplate;

    public AuthService(FirebaseAuth firebaseAuth, UserRepository userRepository, CategoryService categoryService,
                       FirebaseSignInClient firebaseSignInClient, TransactionTemplate transactionTemplate) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.firebaseSignInClient = firebaseSignInClient;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Creates the local user the first time its token is seen, with the default categories, and refreshes
     * email and name afterwards. Categories are only created with the user, so deleted ones never come back.
     */
    public User syncFromToken(String uid, String email, String displayName) {
        try {
            return transactionTemplate.execute(status -> upsert(uid, email, displayName));
        } catch (DataIntegrityViolationException e) {
            // A concurrent first call created the user (and its categories) in the meantime: now it is an update
            return transactionTemplate.execute(status -> upsert(uid, email, displayName));
        }
    }

    private User upsert(String uid, String email, String displayName) {
        User user = userRepository.findById(uid).orElse(null);
        boolean isNew = user == null;
        if (isNew) {
            user = new User();
            user.setUuid(uid);
        }

        user.setEmail(email);
        user.setDisplayName(displayName);
        User saved = userRepository.save(user);

        if (isNew) {
            categoryService.createDefaultCategories(saved);
        }

        return saved;
    }

    public RegisterResponse register(RegisterRequest request) {
        UserRecord firebaseUser = createFirebaseUser(request);
        User user;

        try {
            // The user and its default categories are saved together, or not at all.
            user = transactionTemplate.execute(status -> {
                User newUser = new User();
                newUser.setUuid(firebaseUser.getUid());
                newUser.setEmail(firebaseUser.getEmail());
                newUser.setDisplayName(request.displayName());

                User saved = userRepository.save(newUser);
                categoryService.createDefaultCategories(saved);

                return saved;
            });
        } catch (RuntimeException e) {
            // Keep Firebase and the database consistent: no orphan Firebase account without a local user.
            deleteFirebaseUser(firebaseUser.getUid());
            throw e;
        }

        FirebaseTokens tokens = firebaseSignInClient.signIn(firebaseUser.getEmail(), request.password());

        return new RegisterResponse(user, tokens);
    }

    private UserRecord createFirebaseUser(RegisterRequest request) {
        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .setPassword(request.password());

        if (request.displayName() != null && !request.displayName().isBlank()) {
            createRequest.setDisplayName(request.displayName());
        }

        try {
            return firebaseAuth.createUser(createRequest);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new ConflictException("error.email.already_registered");
            }

            logger.error("Error creating Firebase user", e);
            throw new ExternalServiceException("error.account.creation_failed");
        }
    }

    private void deleteFirebaseUser(String uid) {
        try {
            firebaseAuth.deleteUser(uid);
        } catch (FirebaseAuthException e) {
            logger.error("Could not roll back Firebase user {}", uid, e);
        }
    }
}
