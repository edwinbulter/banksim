package nl.banksim.platform.mtls;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Registreert het {@link MtlsClientFilter} vóór alle andere filters, inclusief Spring Security. */
@AutoConfiguration
@EnableConfigurationProperties(MtlsProperties.class)
public class MtlsAutoConfiguration {

    @Bean
    FilterRegistrationBean<MtlsClientFilter> mtlsClientFilter(MtlsProperties properties) {
        var registration = new FilterRegistrationBean<>(new MtlsClientFilter(properties.allowedClients()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setEnabled(properties.enabled());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
