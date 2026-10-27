package io.josrom.whereismycar.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Picks the response language from the Accept-Language header. Unsupported or missing languages fall back
 * to English. Add a messages_xx.properties file and the locale here to support a new language.
 */
@Configuration
public class LocaleConfig {
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("es")));
        resolver.setDefaultLocale(DEFAULT_LOCALE);

        return resolver;
    }
}
