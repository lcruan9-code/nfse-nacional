package br.com.lc.nfse.api.web;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.web.dto.ContaCriadaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminContaTest extends AbstractPostgresIT {

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void adminKeyCorretaCriaContaEChavesFuncionam() {
        var resp = client().post().uri("/admin/contas")
                .header("X-Admin-Key", "admin_dev_key")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"nome\":\"ACME Servicos\"}")
                .retrieve().toEntity(ContaCriadaResponse.class);

        assertEquals(201, resp.getStatusCode().value());
        assertTrue(resp.getBody().chaveSandbox().startsWith("sk_test_"));
        assertTrue(resp.getBody().chaveProducao().startsWith("sk_live_"));

        // a chave emitida realmente autentica (prova que foi persistida)
        var who = client().get().uri("/v1/whoami")
                .header("X-Api-Key", resp.getBody().chaveSandbox())
                .retrieve().toEntity(String.class);
        assertEquals(200, who.getStatusCode().value());
        assertTrue(who.getBody().contains("ACME Servicos"));
    }

    @Test
    void adminKeyErradaRetorna401() {
        HttpStatusCode status = client().post().uri("/admin/contas")
                .header("X-Admin-Key", "errada")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"nome\":\"X\"}")
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(401, status.value());
    }
}
