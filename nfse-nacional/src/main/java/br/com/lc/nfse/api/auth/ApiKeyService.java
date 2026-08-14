package br.com.lc.nfse.api.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Gera chaves de API no formato {@code sk_test_<hex>} / {@code sk_live_<hex>}, calcula o
 * hash SHA-256 (o que vai pro banco) e deriva o ambiente pelo prefixo. Nunca persiste a
 * chave em texto.
 */
@Service
public class ApiKeyService {

    static final String PREFIXO_SANDBOX = "sk_test_";
    static final String PREFIXO_PRODUCAO = "sk_live_";
    private static final int BYTES_SEGREDO = 32;
    private static final int TAMANHO_PREFIXO_EXIBICAO = 14;

    private final SecureRandom random = new SecureRandom();

    public ChaveGerada gerar(Ambiente ambiente) {
        String prefixoAmbiente = ambiente == Ambiente.SANDBOX ? PREFIXO_SANDBOX : PREFIXO_PRODUCAO;
        byte[] segredo = new byte[BYTES_SEGREDO];
        random.nextBytes(segredo);
        String texto = prefixoAmbiente + HexFormat.of().formatHex(segredo);
        String prefixoExibicao = texto.substring(0, Math.min(TAMANHO_PREFIXO_EXIBICAO, texto.length()));
        return new ChaveGerada(texto, prefixoExibicao, hash(texto), ambiente);
    }

    public String hash(String chave) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(chave.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public Optional<Ambiente> ambienteDe(String chave) {
        if (chave == null) {
            return Optional.empty();
        }
        if (chave.startsWith(PREFIXO_SANDBOX)) {
            return Optional.of(Ambiente.SANDBOX);
        }
        if (chave.startsWith(PREFIXO_PRODUCAO)) {
            return Optional.of(Ambiente.PRODUCAO);
        }
        return Optional.empty();
    }
}
