package nl.banksim.platform.mtls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MtlsClientFilterTests {

    private final MtlsClientFilter filter = new MtlsClientFilter(Set.of("bank-bff"));

    @Test
    void toegestaneClientMagDoor() throws Exception {
        var response = run(certificate("CN=bank-bff"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void andereClientWordtGeweigerd() throws Exception {
        var response = run(certificate("CN=ingress"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void zonderCertificaatWordtGeweigerd() throws Exception {
        var response = run(null);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletResponse run(X509Certificate certificate) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/me");
        if (certificate != null) {
            request.setAttribute(MtlsClientFilter.CERTIFICATE_ATTRIBUTE, new X509Certificate[] {certificate});
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static X509Certificate certificate(String subject) {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal(subject));
        return certificate;
    }
}
