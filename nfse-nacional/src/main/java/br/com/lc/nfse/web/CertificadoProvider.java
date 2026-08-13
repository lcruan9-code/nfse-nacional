package br.com.lc.nfse.web;

import br.com.lc.nfse.core.cert.CertificadoException;
import br.com.lc.nfse.core.cert.CertificadoLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Fornece o certificado de assinatura a partir de {@code nfse.certificado.caminho}
 * (aceita {@code classpath:...} ou caminho de arquivo). Vazio = protótipo não assina.
 * Troca pelo A1 real = mudar caminho/senha na configuração.
 */
@Component
public class CertificadoProvider {

    private static final String PREFIXO_CLASSPATH = "classpath:";

    private final String caminho;
    private final String senha;
    private final CertificadoLoader loader;

    private CertificadoLoader.Certificado cache;

    public CertificadoProvider(@Value("${nfse.certificado.caminho:}") String caminho,
                               @Value("${nfse.certificado.senha:}") String senha,
                               CertificadoLoader loader) {
        this.caminho = caminho;
        this.senha = senha;
        this.loader = loader;
    }

    public synchronized Optional<CertificadoLoader.Certificado> obter() {
        if (caminho == null || caminho.isBlank()) {
            return Optional.empty();
        }
        if (cache == null) {
            try (InputStream is = abrir(caminho)) {
                cache = loader.carregar(is, senha.toCharArray());
            } catch (IOException e) {
                throw new CertificadoException("Falha ao ler certificado: " + caminho, e);
            }
        }
        return Optional.of(cache);
    }

    private InputStream abrir(String c) throws IOException {
        if (c.startsWith(PREFIXO_CLASSPATH)) {
            String recurso = "/" + c.substring(PREFIXO_CLASSPATH.length());
            InputStream is = getClass().getResourceAsStream(recurso);
            if (is == null) {
                throw new CertificadoException("Certificado não encontrado no classpath: " + c, null);
            }
            return is;
        }
        return new FileInputStream(c);
    }
}
