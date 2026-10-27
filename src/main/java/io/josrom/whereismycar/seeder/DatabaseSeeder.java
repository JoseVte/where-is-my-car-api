package io.josrom.whereismycar.seeder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.repository.CategoryRepository;
import io.josrom.whereismycar.repository.SavedLocationRepository;
import io.josrom.whereismycar.repository.UserRepository;

/**
 * Development seeder: creates a test user (in Firebase and in the database) with a few categories.
 * Idempotent, and disabled unless `app.seed.enabled=true`.
 * With `app.seed.exit-after-run=true` the application exits once seeding is done (see seed.sh).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DatabaseSeeder implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

    private record CategorySeed(String label, String icon, String color) {
    }

    private static final List<CategorySeed> CATEGORIES = List.of(
            new CategorySeed(Category.HOME_LABEL, "home", "#E65100"),
            new CategorySeed(Category.FAVORITES_LABEL, "favorite", "#E91E63"),
            new CategorySeed(Category.OTHERS_LABEL, "route", "#7D6E5D"),
            new CategorySeed("Airport", "flight", "#9C27B0")
    );

    private record SavedLocationSeed(String categoryLabel, String displayName, String address, String city,
                                     String postalCode, String state, double latitude, double longitude,
                                     long daysAgo, String note) {
    }

    private static final List<SavedLocationSeed> SAVED_LOCATIONS = List.of(
            new SavedLocationSeed(Category.HOME_LABEL, "Garage at home", "Calle Gran Via 28", "Madrid", "28013", "Madrid",
                    40.4200, -3.7050, 0, "Level -2, spot 14"),
            new SavedLocationSeed("Airport", "Madrid-Barajas T4", "Avenida de la Hispanidad", "Madrid", "28042",
                    "Madrid", 40.4719, -3.5626, 3, "Long stay parking, zone B"),
            new SavedLocationSeed(Category.FAVORITES_LABEL, "Plaza Mayor car park", "Plaza Mayor 1", "Madrid", "28012",
                    "Madrid", 40.4155, -3.7074, 10, null),
            new SavedLocationSeed(Category.OTHERS_LABEL, "Xanadu shopping centre", "Calle Puerto de Navacerrada", "Arroyomolinos",
                    "28939", "Madrid", 40.2872, -3.8765, 21, "Near the cinema entrance"),
            new SavedLocationSeed("Airport", "Barcelona El Prat T1", "Aeroport de Barcelona", "El Prat de Llobregat",
                    "08820", "Barcelona", 41.2974, 2.0833, 45, "Parking P1")
    );

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SavedLocationRepository savedLocationRepository;
    private final String email;
    private final String password;
    private final boolean exitAfterRun;
    private final ConfigurableApplicationContext context;

    public DatabaseSeeder(FirebaseAuth firebaseAuth,
                          UserRepository userRepository,
                          CategoryRepository categoryRepository,
                          SavedLocationRepository savedLocationRepository,
                          @Value("${app.seed.email}") String email,
                          @Value("${app.seed.password}") String password,
                          @Value("${app.seed.exit-after-run:false}") boolean exitAfterRun,
                          ConfigurableApplicationContext context) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.savedLocationRepository = savedLocationRepository;
        this.email = email;
        this.password = password;
        this.exitAfterRun = exitAfterRun;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws FirebaseAuthException {
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Set SEED_EMAIL and SEED_PASSWORD in .env to run the seeder");
        }

        User user = seedUser();

        seedCategories(user);
        seedSavedLocations(user);

        logger.info("Seeded test user {} ({})", user.getEmail(), user.getUuid());

        if (exitAfterRun) {
            // System.exit because the Firebase SDK keeps non-daemon threads alive.
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }

    private User seedUser() throws FirebaseAuthException {
        UserRecord firebaseUser = findOrCreateFirebaseUser();

        return userRepository.findById(firebaseUser.getUid()).orElseGet(() -> {
            User user = new User();
            user.setUuid(firebaseUser.getUid());
            user.setEmail(firebaseUser.getEmail());
            user.setDisplayName("Test User");

            return userRepository.save(user);
        });
    }

    private UserRecord findOrCreateFirebaseUser() throws FirebaseAuthException {
        try {
            UserRecord existing = firebaseAuth.getUserByEmail(email);

            // .env is the source of truth for the seed password
            return firebaseAuth.updateUser(new UserRecord.UpdateRequest(existing.getUid()).setPassword(password));
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw e;
            }
        }

        return firebaseAuth.createUser(new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)
                .setDisplayName("Test User"));
    }

    private void seedCategories(User user) {
        if (categoryRepository.findByUserUuid(user.getUuid()).iterator().hasNext()) {
            return;
        }

        CATEGORIES.forEach(seed -> {
            Category category = new Category();
            category.setLabel(seed.label());
            category.setIcon(seed.icon());
            category.setColor(seed.color());
            category.setUser(user);

            categoryRepository.save(category);
        });
    }

    private void seedSavedLocations(User user) {
        if (savedLocationRepository.existsByUserUuid(user.getUuid())) {
            return;
        }

        Map<String, Category> categoriesByLabel = StreamSupport
                .stream(categoryRepository.findByUserUuid(user.getUuid()).spliterator(), false)
                .collect(Collectors.toMap(Category::getLabel, Function.identity(), (first, second) -> first));

        SAVED_LOCATIONS.forEach(seed -> {
            SavedLocation location = new SavedLocation();
            location.setDisplayName(seed.displayName());
            location.setCountry("Spain");
            location.setCity(seed.city());
            location.setAddress(seed.address());
            location.setLatitude(seed.latitude());
            location.setLongitude(seed.longitude());
            location.setAddressData(new HashMap<>(Map.of(
                    "postalCode", seed.postalCode(),
                    "state", seed.state(),
                    "countryCode", "ES")));
            location.setDate(Date.from(Instant.now().minus(seed.daysAgo(), ChronoUnit.DAYS)));
            location.setNote(seed.note());
            location.setCategory(categoriesByLabel.get(seed.categoryLabel()));
            location.setUser(user);

            savedLocationRepository.save(location);
        });
    }
}
