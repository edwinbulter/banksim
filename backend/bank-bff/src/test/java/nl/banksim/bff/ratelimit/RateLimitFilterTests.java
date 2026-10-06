package nl.banksim.bff.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.bucket4j.distributed.proxy.ProxyManager;

import nl.banksim.bff.BffTest;

/** De login-limiet met de echte buckets in PostgreSQL. */
@SpringBootTest
class RateLimitFilterTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BffTest.registreer(registry);
    }

    @Autowired
    ProxyManager<String> buckets;

    private final String ip = "test-" + UUID.randomUUID();

    @Test
    void weigertBovenDeLimietMet429() throws Exception {
        var filter = new RateLimitFilter(buckets, RateLimitFilter.Soort.LOGIN, 2);
        assertThat(login(filter).getStatus()).isEqualTo(200);
        assertThat(login(filter).getStatus()).isEqualTo(200);
        MockHttpServletResponse geweigerd = login(filter);
        assertThat(geweigerd.getStatus()).isEqualTo(429);
        assertThat(geweigerd.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void eenNieuweLimietGeldtOokVoorEenBestaandeBucket() throws Exception {
        var oud = new RateLimitFilter(buckets, RateLimitFilter.Soort.LOGIN, 1);
        assertThat(login(oud).getStatus()).isEqualTo(200);
        assertThat(login(oud).getStatus()).isEqualTo(429);

        var nieuw = new RateLimitFilter(buckets, RateLimitFilter.Soort.LOGIN, 5);
        for (int i = 0; i < 5; i++) {
            assertThat(login(nieuw).getStatus()).isEqualTo(200);
        }
        assertThat(login(nieuw).getStatus()).isEqualTo(429);
    }

    @Test
    void anderePadenTellenNietMee() throws Exception {
        var filter = new RateLimitFilter(buckets, RateLimitFilter.Soort.LOGIN, 1);
        for (int i = 0; i < 3; i++) {
            var request = new MockHttpServletRequest("GET", "/api/me");
            request.addHeader("X-Forwarded-For", ip);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse login(RateLimitFilter filter) throws Exception {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        request.addHeader("X-Forwarded-For", ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
