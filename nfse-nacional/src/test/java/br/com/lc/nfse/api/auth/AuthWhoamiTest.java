package br.com.lc.nfse.api.auth;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.tenant.Conta;
import br.com.lc.nfse.api.tenant.ContaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWhoamiTest extends AbstractPostgresIT {

    @Value("${local.server.port}")
    int port;
    @Autowired
    ContaRepository contaRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    ApiKeyService apiKeyService;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String semearContaComChave(String nome, String chave, Ambiente ambiente) {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta conta = contaRepository.save(Conta.nova(nome, agora));
        apiKeyRepository.save(ApiKey.nova(conta.getId(), ambiente,
                apiKeyService.hash(chave), chave.substring(0, 14), agora));
        return conta.getId().toString();
    }

    @Test
    void chaveValidaRetornaWhoami() {
        String chave = "sk_test_deadbeefdeadbeefdeadbeefdeadbeef";
        semearContaComChave("Cliente Teste", chave, Ambiente.SANDBOX);

        var resp = client().get().uri("/v1/whoami").header("X-Api-Key", chave)
                .retrieve().toEntity(String.class);

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("SANDBOX"));
        assertTrue(resp.getBody().contains("Cliente Teste"));
    }

    @Test
    void semChaveRetorna401() {
        HttpStatusCode status = client().get().uri("/v1/whoami")
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(401, status.value());
    }

    @Test
    void chaveInexistenteRetorna401() {
        HttpStatusCode status = client().get().uri("/v1/whoami")
                .header("X-Api-Key", "sk_test_naoexistenaoexistenaoexistenaoex")
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(401, status.value());
    }
}
