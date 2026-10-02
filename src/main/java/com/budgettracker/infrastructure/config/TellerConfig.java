package com.budgettracker.infrastructure.config;

import com.budgettracker.infrastructure.teller.TellerClient;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TellerConfig {

    private static final String TELLER_BASE_URL = "https://api.teller.io";

    @Bean
    TellerClient tellerClient(
            @Value("${app.teller.cert-pem-base64:}") String certBase64,
            @Value("${app.teller.key-pem-base64:}") String keyBase64) {
        RestClient http = certBase64.isBlank() || keyBase64.isBlank()
                ? plainClient()
                : mtlsClient(certBase64, keyBase64);
        return new TellerClient(http);
    }

    private RestClient plainClient() {
        // No mTLS — used in local dev and tests; Teller will reject real calls.
        return RestClient.builder().baseUrl(TELLER_BASE_URL).build();
    }

    private RestClient mtlsClient(String certBase64, String keyBase64) {
        try {
            SSLContext sslContext = buildSslContext(certBase64, keyBase64);
            HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
            return RestClient.builder()
                    .baseUrl(TELLER_BASE_URL)
                    .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure Teller mTLS client.", e);
        }
    }

    private SSLContext buildSslContext(String certBase64, String keyBase64) throws Exception {
        String certPem = new String(Base64.getMimeDecoder().decode(certBase64));
        String keyPem = new String(Base64.getMimeDecoder().decode(keyBase64));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pemToBytes(certPem)));

        PrivateKey privateKey = parsePrivateKey(pemToBytes(keyPem));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, new char[0]);
        ks.setKeyEntry("teller", privateKey, new char[0], new Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    /** Strip PEM headers/footers and base64-decode to raw DER bytes. */
    private static byte[] pemToBytes(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN[^-]*-----", "")
                .replaceAll("-----END[^-]*-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }

    /** Tries RSA then EC; Teller certificates are RSA 2048. */
    private static PrivateKey parsePrivateKey(byte[] derBytes) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(derBytes);
        for (String algo : new String[]{"RSA", "EC"}) {
            try {
                return KeyFactory.getInstance(algo).generatePrivate(spec);
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("Cannot parse Teller private key (tried RSA and EC PKCS8).");
    }
}
