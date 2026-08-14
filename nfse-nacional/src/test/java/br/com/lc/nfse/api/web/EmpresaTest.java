package br.com.lc.nfse.api.web;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.ApiKey;
import br.com.lc.nfse.api.auth.ApiKeyRepository;
import br.com.lc.nfse.api.auth.ApiKeyService;
import br.com.lc.nfse.api.tenant.Conta;
import br.com.lc.nfse.api.tenant.ContaRepository;
import br.com.lc.nfse.api.web.dto.EmpresaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpresaTest extends AbstractPostgresIT {

    @Value("${local.server.port}")
    int port;
    @Autowired
    ContaRepository contaRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    ApiKeyService apiKeyService;

    private static final String EMP_JSON = """
            {"cnpj":"11222333000181","razaoSocial":"Loja A","inscricaoMunicipal":"123","codMunIbge":"3550308"}
            """;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String semear(String nome, String chave) {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta c = contaRepository.save(Conta.nova(nome, agora));
        apiKeyRepository.save(ApiKey.nova(c.getId(), Ambiente.SANDBOX,
                apiKeyService.hash(chave), chave.substring(0, 14), agora));
        return chave;
    }

    @Test
    void criaListaEIsolaEntreContas() {
        String chaveA = semear("Conta A", "sk_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        String chaveB = semear("Conta B", "sk_test_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        var criada = client().post().uri("/v1/empresas").header("X-Api-Key", chaveA)
                .contentType(MediaType.APPLICATION_JSON).body(EMP_JSON)
                .retrieve().toEntity(EmpresaResponse.class);
        assertEquals(201, criada.getStatusCode().value());
        UUID idA = criada.getBody().id();

        var listaA = client().get().uri("/v1/empresas").header("X-Api-Key", chaveA)
                .retrieve().toEntity(String.class);
        assertTrue(listaA.getBody().contains("11222333000181"));

        var listaB = client().get().uri("/v1/empresas").header("X-Api-Key", chaveB)
                .retrieve().toEntity(String.class);
        assertFalse(listaB.getBody().contains("11222333000181"));

        HttpStatusCode crossTenant = client().get().uri("/v1/empresas/" + idA)
                .header("X-Api-Key", chaveB)
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(404, crossTenant.value(), "isolamento cross-tenant");
    }

    @Test
    void cnpjDuplicadoNaMesmaContaRetorna409() {
        String chave = semear("Conta C", "sk_test_cccccccccccccccccccccccccccccccc");
        client().post().uri("/v1/empresas").header("X-Api-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).body(EMP_JSON)
                .retrieve().toBodilessEntity();

        HttpStatusCode dup = client().post().uri("/v1/empresas").header("X-Api-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).body(EMP_JSON)
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(409, dup.value());
    }

    @Test
    void ibgeInvalidoRetorna422() {
        String chave = semear("Conta D", "sk_test_dddddddddddddddddddddddddddddddd");
        String bad = """
                {"cnpj":"11222333000181","razaoSocial":"Loja","inscricaoMunicipal":null,"codMunIbge":"123"}
                """;

        HttpStatusCode status = client().post().uri("/v1/empresas").header("X-Api-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).body(bad)
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(422, status.value());
    }
}
