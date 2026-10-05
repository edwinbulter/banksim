package nl.banksim.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** De kubelet-probes moeten zonder token en zonder clientcertificaat bij de health-endpoints kunnen. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.ssl.enabled=false",
        "banksim.cursor-sleutel=test-sleutel-van-minstens-32-tekens!!",
        "spring.ssl.bundle.pem.server.keystore.certificate=",
        "spring.ssl.bundle.pem.server.keystore.private-key=",
        "spring.ssl.bundle.pem.server.truststore.certificate=",
        "management.server.port=0"
})
class ManagementPortTests {

    @LocalManagementPort
    int managementPort;

    @Test
    void livenessEnReadinessZijnZonderAuthenticatieBereikbaar() {
        RestClient client = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl("http://localhost:" + managementPort)
                .build();
        assertThat(client.get().uri("/actuator/health/liveness").retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(client.get().uri("/actuator/health/readiness").retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(200);
    }
}
