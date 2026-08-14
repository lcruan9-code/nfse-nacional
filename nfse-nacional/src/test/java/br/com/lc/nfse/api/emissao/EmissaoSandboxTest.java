package br.com.lc.nfse.api.emissao;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.ApiKey;
import br.com.lc.nfse.api.auth.ApiKeyRepository;
import br.com.lc.nfse.api.auth.ApiKeyService;
import br.com.lc.nfse.api.emissao.dto.EmissaoResponse;
import br.com.lc.nfse.api.tenant.Conta;
import br.com.lc.nfse.api.tenant.ContaRepository;
import br.com.lc.nfse.api.tenant.Empresa;
import br.com.lc.nfse.api.tenant.EmpresaRepository;
import br.com.lc.nfse.api.tenant.OpSimplesNacional;
import br.com.lc.nfse.api.tenant.RegimeEspecialTributacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmissaoSandboxTest extends AbstractPostgresIT {

    @Value("${local.server.port}")
    int port;
    @Autowired ContaRepository contaRepo;
    @Autowired ApiKeyRepository keyRepo;
    @Autowired ApiKeyService keySvc;
    @Autowired EmpresaRepository empresaRepo;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private record Setup(String chave, UUID empresaId) {}

    private Setup setup(String nome, String chave, Ambiente ambiente) {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta c = contaRepo.save(Conta.nova(nome, agora));
        keyRepo.save(ApiKey.nova(c.getId(), ambiente, keySvc.hash(chave), chave.substring(0, 14), agora));
        Empresa e = empresaRepo.save(Empresa.nova(c.getId(), "11222333000181", nome + " Ltda", "123",
                "3550308", OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora));
        return new Setup(chave, e.getId());
    }

    private String reqJson(UUID empresaId, String simular) {
        return """
                {"empresaId":"%s",
                 "servico":{"codTribNacional":"010101","descricao":"Consultoria em TI","codMunPrestacao":"3550308"},
                 "valores":{"valorServico":"1500.00","tributacaoIssqn":1,"tipoRetencaoIssqn":1},
                 "simular":"%s"}
                """.formatted(empresaId, simular);
    }

    private EmissaoResponse emitir(Setup s, String simular) {
        return client().post().uri("/v1/nfse").header("X-Api-Key", s.chave())
                .contentType(MediaType.APPLICATION_JSON).body(reqJson(s.empresaId(), simular))
                .retrieve().toEntity(EmissaoResponse.class).getBody();
    }

    @Test
    void emiteAutorizadaEConsulta() {
        Setup s = setup("Cliente Sandbox", "sk_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", Ambiente.SANDBOX);

        EmissaoResponse emitida = emitir(s, "AUTORIZADA");
        assertEquals("AUTORIZADA", emitida.status());

        var consulta = client().get().uri("/v1/nfse/" + emitida.id()).header("X-Api-Key", s.chave())
                .retrieve().toEntity(EmissaoResponse.class);
        assertEquals(200, consulta.getStatusCode().value());
        assertEquals("AUTORIZADA", consulta.getBody().status());
        assertEquals(50, consulta.getBody().chaveAcesso().length());
        assertTrue(consulta.getBody().xmlDps().contains("<Signature"), "DPS deveria estar assinada");
        assertTrue(consulta.getBody().xmlDps().contains("<IBSCBS>"), "DPS deveria ter o grupo IBSCBS (RTC)");
        // IBS/CBS apurados sobre R$ 1500,00 com CBS 0,90% e IBS 0,10% (defaults de homologação)
        assertEquals("0.90", consulta.getBody().aliquotaCbs());
        assertEquals("13.50", consulta.getBody().valorCbs());
        assertEquals("0.10", consulta.getBody().aliquotaIbs());
        assertEquals("1.50", consulta.getBody().valorIbs());
    }

    @Test
    void simularRejeitada() {
        Setup s = setup("Cliente Rej", "sk_test_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", Ambiente.SANDBOX);

        EmissaoResponse emitida = emitir(s, "REJEITADA");

        assertEquals("REJEITADA", emitida.status());
        assertTrue(emitida.motivo() != null && !emitida.motivo().isBlank());
        assertNull(emitida.chaveAcesso());
    }

    @Test
    void dadoInvalidoRetorna422() {
        Setup s = setup("Cliente Inv", "sk_test_cccccccccccccccccccccccccccccccc", Ambiente.SANDBOX);
        String bad = """
                {"empresaId":"%s",
                 "servico":{"codTribNacional":"010101","descricao":"X","codMunPrestacao":"123"},
                 "valores":{"valorServico":"1500.00","tributacaoIssqn":1,"tipoRetencaoIssqn":1}}
                """.formatted(s.empresaId());

        HttpStatusCode status = client().post().uri("/v1/nfse").header("X-Api-Key", s.chave())
                .contentType(MediaType.APPLICATION_JSON).body(bad)
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(422, status.value());
    }

    @Test
    void crossTenantConsultaRetorna404() {
        Setup a = setup("Conta A", "sk_test_dddddddddddddddddddddddddddddddd", Ambiente.SANDBOX);
        Setup b = setup("Conta B", "sk_test_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", Ambiente.SANDBOX);
        UUID idA = emitir(a, "AUTORIZADA").id();

        HttpStatusCode status = client().get().uri("/v1/nfse/" + idA).header("X-Api-Key", b.chave())
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(404, status.value());
    }

    @Test
    void chaveProducaoRetorna501() {
        Setup s = setup("Cliente Prod", "sk_live_ffffffffffffffffffffffffffffffff", Ambiente.PRODUCAO);

        HttpStatusCode status = client().post().uri("/v1/nfse").header("X-Api-Key", s.chave())
                .contentType(MediaType.APPLICATION_JSON).body(reqJson(s.empresaId(), "AUTORIZADA"))
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(501, status.value());
    }
}
