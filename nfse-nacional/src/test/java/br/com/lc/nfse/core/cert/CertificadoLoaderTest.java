package br.com.lc.nfse.core.cert;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificadoLoaderTest {

    private final CertificadoLoader loader = new CertificadoLoader();

    @Test
    void carregaCertificadoDeTeste() {
        InputStream p12 = getClass().getResourceAsStream("/certs/teste.p12");
        assertNotNull(p12, "teste.p12 deve estar em src/test/resources/certs");

        CertificadoLoader.Certificado cert = loader.carregar(p12, "changeit".toCharArray());

        assertNotNull(cert.privateKey());
        assertNotNull(cert.certificate());
        assertEquals("RSA", cert.privateKey().getAlgorithm());
    }

    @Test
    void senhaErradaLancaCertificadoException() {
        InputStream p12 = getClass().getResourceAsStream("/certs/teste.p12");

        assertThrows(CertificadoException.class,
                () -> loader.carregar(p12, "senha-errada".toCharArray()));
    }
}
