package nl.banksim.bff;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.tomakehurst.wiremock.WireMockServer;

import dasniko.testcontainers.keycloak.KeycloakContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Volledige OIDC-login (authorization code + PKCE) tegen een echte Keycloak, met twee BFF-instanties die één
 * sessiedatabase delen (TO §2, §13). Na inloggen op instantie A moet instantie B de sessie en het access token
 * kennen; de beheerder komt op /admin terecht.
 */
class KeycloakLoginTests {

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.0")
            .withRealmImportFile("banksim-realm.json");
    static final WireMockServer API = new WireMockServer(options().dynamicPort());
    static ConfigurableApplicationContext a;
    static ConfigurableApplicationContext b;
    static int poortA;
    static int poortB;

    @BeforeAll
    static void start() throws IOException {
        KEYCLOAK.start();
        API.start();
        API.stubFor(get("/api/me").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"naam\":\"via bank-api\"}")));
        poortA = vrijePoort();
        poortB = vrijePoort();
        a = start(poortA);
        b = start(poortB);
    }

    @AfterAll
    static void stop() {
        if (a != null) {
            a.close();
        }
        if (b != null) {
            b.close();
        }
        API.stop();
        KEYCLOAK.stop();
    }

    @Test
    void klantLogtInOpAEnGebruiktDeSessieOpB() throws Exception {
        var browser = new Browser();
        String landing = browser.login(poortA, "jdevries", "klant-wachtwoord");
        assertThat(landing).isEqualTo("http://localhost:" + poortA + "/");

        HttpResponse<String> viaB = browser.get("http://localhost:" + poortB + "/api/me");
        assertThat(viaB.statusCode()).isEqualTo(200);
        assertThat(viaB.body()).contains("via bank-api");

        // bank-api kreeg een echt access token van Keycloak met audience bank-api, en geen browsercookies.
        var verzoek = API.findAll(getRequestedFor(urlEqualTo("/api/me")).withHeader("Authorization", matching("Bearer .+"))).getLast();
        String token = verzoek.getHeader("Authorization").substring("Bearer ".length());
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"aud\":\"bank-api\"").contains("\"preferred_username\":\"jdevries\"");
        assertThat(verzoek.containsHeader("Cookie")).isFalse();
    }

    @Test
    void beheerderKomtOpAdmin() throws Exception {
        var browser = new Browser();
        assertThat(browser.login(poortA, "beheerder", "beheer-wachtwoord")).isEqualTo("http://localhost:" + poortA + "/admin");
    }

    @Test
    void zonderSessieGeenToegang() throws Exception {
        assertThat(new Browser().get("http://localhost:" + poortB + "/api/me").statusCode()).isEqualTo(401);
    }

    private static ConfigurableApplicationContext start(int poort) {
        Map<String, Object> p = BffTest.eigenschappen();
        String keycloak = KEYCLOAK.getAuthServerUrl().replaceAll("/$", "");
        p.put("server.port", poort);
        p.put("management.server.port", 0);
        p.put("banksim.bff.public-url", "http://localhost:" + poort);
        p.put("banksim.bff.api-url", "http://localhost:" + API.port());
        p.put("banksim.bff.keycloak.public-url", keycloak);
        p.put("banksim.bff.keycloak.internal-url", keycloak);
        p.put("banksim.bff.sessie.secure-cookie", "false");
        p.put("spring.flyway.enabled", poort == poortA ? "true" : "false");
        // Als argumenten: die gaan boven application.yaml (standaard-eigenschappen niet).
        String[] argumenten = p.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        return new SpringApplicationBuilder(BankBffApplication.class).run(argumenten);
    }

    private static int vrijePoort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String foutmelding(String html) {
        Matcher m = Pattern.compile("(?s)<title>(.*?)</title>.*?(?:id=\"input-error[^>]*>(.*?)<|kc-feedback-text\">(.*?)<)").matcher(html);
        String tekst = html.replaceAll("(?s)<script.*?</script>", "").replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
        return tekst.substring(0, Math.min(400, tekst.length()));
    }

    /** Keycloak zet zijn cookies altijd met Secure; de test draait zonder TLS. */
    record ZonderSecureVlag(CookieStore store) implements CookieStore {

        @Override
        public void add(URI uri, HttpCookie cookie) {
            cookie.setSecure(false);
            store.add(uri, cookie);
        }

        @Override
        public List<HttpCookie> get(URI uri) {
            return store.get(uri);
        }

        @Override
        public List<HttpCookie> getCookies() {
            return store.getCookies();
        }

        @Override
        public List<URI> getURIs() {
            return store.getURIs();
        }

        @Override
        public boolean remove(URI uri, HttpCookie cookie) {
            return store.remove(uri, cookie);
        }

        @Override
        public boolean removeAll() {
            return store.removeAll();
        }
    }

    /** Een minimale browser: cookies bewaren en redirects stap voor stap volgen. */
    static final class Browser {

        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(new ZonderSecureVlag(new CookieManager().getCookieStore()), CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpResponse<String> get(String url) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        /** @return de URL waar de BFF na het inloggen naartoe stuurt */
        String login(int poort, String gebruiker, String wachtwoord) throws Exception {
            HttpResponse<String> start = get("http://localhost:" + poort + "/oauth2/authorization/keycloak");
            String keycloakUrl = start.headers().firstValue("Location").orElseThrow();
            assertThat(keycloakUrl).contains("code_challenge_method=S256");

            HttpResponse<String> loginPagina = get(keycloakUrl);
            Matcher actie = Pattern.compile("id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"").matcher(loginPagina.body());
            assertThat(actie.find()).isTrue();
            String formulier = "username=" + URLEncoder.encode(gebruiker, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(wachtwoord, StandardCharsets.UTF_8);
            HttpResponse<String> ingelogd = client.send(HttpRequest.newBuilder(URI.create(actie.group(1).replace("&amp;", "&")))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formulier)).build(), HttpResponse.BodyHandlers.ofString());
            String terug = ingelogd.headers().firstValue("Location")
                    .orElseThrow(() -> new AssertionError("Geen redirect na login (" + ingelogd.statusCode() + "): "
                            + foutmelding(ingelogd.body())));
            assertThat(terug).startsWith("http://localhost:" + poort + "/login/oauth2/code/keycloak");

            HttpResponse<String> klaar = get(terug);
            return klaar.headers().firstValue("Location").orElseThrow();
        }
    }
}
