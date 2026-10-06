package nl.banksim.bff.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting (TO §10.1, §11 A06/A07): inlogpogingen per IP-adres en boekingen per gebruiker. De buckets
 * staan in PostgreSQL, dus de limiet geldt voor alle BFF-replica's samen.
 *
 * <p>Twee instanties: {@link Soort#LOGIN} staat vóór Spring Security (dat de login-redirect zelf afhandelt en
 * de keten daar stopt), {@link Soort#BOEKINGEN} erna (heeft de ingelogde gebruiker nodig).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public enum Soort { LOGIN, BOEKINGEN }

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<String> buckets;
    private final Soort soort;
    private final Supplier<BucketConfiguration> configuratie;

    public RateLimitFilter(ProxyManager<String> buckets, Soort soort, int perMinuut) {
        this.buckets = buckets;
        this.soort = soort;
        this.configuratie = () -> perMinuut(perMinuut);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String pad = request.getRequestURI();
        ConsumptionProbe probe = null;
        if (soort == Soort.LOGIN && pad.startsWith("/oauth2/authorization/")) {
            probe = buckets.getProxy("login:" + clientIp(request), configuratie).tryConsumeAndReturnRemaining(1);
        } else if (soort == Soort.BOEKINGEN && HttpMethod.POST.matches(request.getMethod())
                && (pad.equals("/api/payments") || pad.equals("/api/transfers"))) {
            probe = buckets.getProxy("boeken:" + gebruiker(request), configuratie).tryConsumeAndReturnRemaining(1);
        }
        if (probe != null && !probe.isConsumed()) {
            long seconden = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            log.warn("Rate limit bereikt voor {}", pad);
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconden));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"type":"https://banksim.local/problems/te-veel-verzoeken","title":"Te veel verzoeken","status":429,\
                    "detail":"Je hebt dit te vaak achter elkaar geprobeerd. Wacht %d seconden en probeer het opnieuw."}"""
                    .formatted(seconden));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Alleen ingress-nginx kan de BFF bereiken (mTLS), dus X-Forwarded-For is betrouwbaar. */
    static String clientIp(HttpServletRequest request) {
        String doorgestuurd = request.getHeader("X-Forwarded-For");
        if (doorgestuurd != null && !doorgestuurd.isBlank()) {
            return doorgestuurd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String gebruiker(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "ip:" + clientIp(request);
    }

    private static BucketConfiguration perMinuut(int aantal) {
        return BucketConfiguration.builder()
                .addLimit(limiet -> limiet.capacity(aantal).refillGreedy(aantal, Duration.ofMinutes(1)))
                .build();
    }
}
