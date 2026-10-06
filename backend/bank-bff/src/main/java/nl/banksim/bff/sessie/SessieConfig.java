package nl.banksim.bff.sessie;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import nl.banksim.bff.BffProperties;

/** Gedeelde, versleutelde sessie in PostgreSQL en het sessiecookie (TO §10.1). */
@Configuration
class SessieConfig {

    /**
     * Spring Session JDBC zet attributen met een ConversionService om naar en van bytes; hier zit de
     * versleuteling tussen.
     */
    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> versleuteldeSessie(BffProperties properties) {
        var versleuteling = new SessieVersleuteling(properties.sessie().sleutel(), getClass().getClassLoader());
        var conversie = new GenericConversionService();
        conversie.addConverter(Object.class, byte[].class, versleuteling::versleutel);
        conversie.addConverter(byte[].class, Object.class, versleuteling::ontsleutel);
        return repository -> repository.setConversionService(conversie);
    }

    /**
     * De {@code __Host-}-prefix dwingt Secure, Path=/ en geen Domain af, zodat het cookie alleen naar
     * bank.localtest.me gaat.
     */
    @Bean
    CookieSerializer cookieSerializer(BffProperties properties) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("__Host-SESSION");
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(properties.sessie().secureCookie());
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Strict");
        serializer.setUseBase64Encoding(true);
        return serializer;
    }
}
