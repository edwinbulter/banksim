package nl.banksim.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Sessiecookie volgens TO §10.1: de {@code __Host-}-prefix dwingt Secure, Path=/ en geen Domain af, zodat
 * het cookie alleen naar bank.localtest.me gaat.
 */
@Configuration
class SessionCookieConfig {

    @Bean
    CookieSerializer cookieSerializer() {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("__Host-SESSION");
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(true);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Strict");
        serializer.setUseBase64Encoding(true);
        return serializer;
    }
}
