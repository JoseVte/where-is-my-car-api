package io.josrom.whereismycar.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.mysql.MySQLContainer;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import io.josrom.whereismycar.component.FirebaseSignInClient;
import io.josrom.whereismycar.component.RegistrationRateLimiter;
import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.repository.CategoryRepository;
import io.josrom.whereismycar.repository.SavedLocationRepository;
import io.josrom.whereismycar.repository.UserRepository;

/**
 * Base class for integration tests: full application context, real MySQL (Testcontainers, started once
 * for the whole test run) and mocked Firebase. Requests are authenticated with a fake JWT, so the
 * Firebase signature check is bypassed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    @MockitoBean
    protected FirebaseApp firebaseApp;

    @MockitoBean
    protected FirebaseAuth firebaseAuth;

    @MockitoBean
    protected FirebaseSignInClient firebaseSignInClient;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected SavedLocationRepository savedLocationRepository;

    @Autowired
    protected RegistrationRateLimiter registrationRateLimiter;

    @BeforeEach
    void cleanDatabase() {
        registrationRateLimiter.reset();
        savedLocationRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Authenticates the request as the given Firebase UID. */
    protected static RequestPostProcessor as(String uid) {
        return jwt().jwt(token -> token.subject(uid).claim("email", uid + "@example.com").claim("name", "User " + uid));
    }

    protected User createUser(String uid) {
        User user = new User();
        user.setUuid(uid);
        user.setEmail(uid + "@example.com");
        user.setDisplayName("User " + uid);

        return userRepository.save(user);
    }

    protected Category createCategory(User user, String label) {
        Category category = new Category();
        category.setUser(user);
        category.setLabel(label);
        category.setIcon("place");
        category.setColor("#000000");

        return categoryRepository.save(category);
    }

    protected SavedLocation createLocation(User user, Category category, String name, java.time.Instant date) {
        SavedLocation location = new SavedLocation();
        location.setUser(user);
        location.setCategory(category);
        location.setDisplayName(name);
        location.setLatitude(40.0);
        location.setLongitude(-3.0);
        location.setDate(java.util.Date.from(date));

        return savedLocationRepository.save(location);
    }
}
