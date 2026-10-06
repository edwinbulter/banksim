package nl.banksim.bff.ratelimit;

import java.time.Duration;

import javax.sql.DataSource;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nl.banksim.bff.BffProperties;

@Configuration
class RateLimitConfig {

    @Bean
    ProxyManager<String> buckets(DataSource dataSource) {
        return Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("bff.bucket").idColumn("id").stateColumn("state").expiresAtColumn("expires_at")
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }

    /** Vóór Spring Security: dat handelt de login-redirect zelf af en stopt daar de filterketen. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> loginLimiet(ProxyManager<String> buckets, BffProperties properties) {
        var registratie = new FilterRegistrationBean<>(new RateLimitFilter(buckets, RateLimitFilter.Soort.LOGIN,
                properties.limiet().loginPerMinuut()));
        registratie.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        registratie.addUrlPatterns("/oauth2/authorization/*");
        return registratie;
    }

    /** Na Spring Security, zodat de limiet op boekingen per ingelogde gebruiker telt. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> boekingenLimiet(ProxyManager<String> buckets, BffProperties properties) {
        var registratie = new FilterRegistrationBean<>(new RateLimitFilter(buckets, RateLimitFilter.Soort.BOEKINGEN,
                properties.limiet().boekingenPerMinuut()));
        registratie.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        registratie.addUrlPatterns("/api/*");
        return registratie;
    }
}
