package br.com.lc.nfse.core.cert;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Carrega um certificado A1 em formato PKCS12 (.p12/.pfx) e expõe a chave privada
 * e o certificado X.509. No protótipo aponta para um autoassinado de teste; em
 * produção, para o A1 ICP-Brasil (só troca o arquivo/senha). Unidade pura — sem Spring.
 */
public class CertificadoLoader {

    /** Par (chave privada, certificado) pronto para assinar. */
    public record Certificado(PrivateKey privateKey, X509Certificate certificate) {}

    public Certificado carregar(InputStream p12, char[] senha) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(p12, senha);

            String alias = primeiroAliasComChave(ks);
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, senha);
            X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);

            if (privateKey == null || certificate == null) {
                throw new IllegalStateException("Keystore sem chave privada/certificado utilizável no alias " + alias);
            }
            return new Certificado(privateKey, certificate);
        } catch (Exception e) {
            throw new CertificadoException("Falha ao carregar certificado PKCS12", e);
        }
    }

    private String primeiroAliasComChave(KeyStore ks) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("Nenhum alias com chave privada no keystore");
    }
}
