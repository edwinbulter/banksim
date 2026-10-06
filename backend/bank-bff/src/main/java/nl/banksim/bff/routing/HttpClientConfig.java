package nl.banksim.bff.routing;

import java.net.http.HttpClient;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BFF → bank-api altijd over HTTP/1.1. De JDK-client probeert anders HTTP/2 (zonder TLS via een upgrade), wat
 * niet elke server ondersteunt; bank-api (Tomcat) praat zonder extra configuratie toch HTTP/1.1. Timeouts komen
 * uit {@code spring.http.clients.*}; de JVM-brede SSLContext levert het clientcertificaat.
 */
@Configuration
class HttpClientConfig {

    @Bean
    ClientHttpRequestFactoryBuilder<?> clientHttpRequestFactoryBuilder() {
        JdkClientHttpRequestFactoryBuilder builder = ClientHttpRequestFactoryBuilder.jdk();
        return builder.withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1));
    }
}
