package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.ApiKey;
import br.com.lc.nfse.api.auth.ApiKeyRepository;
import br.com.lc.nfse.api.auth.ApiKeyService;
import br.com.lc.nfse.api.municipal.dto.EmissaoMunicipalResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmissaoMunicipalTest extends AbstractPostgresIT {

    @Value("${local.server.port}") int port;
    @Autowired ContaRepository contaRepo;
    @Autowired ApiKeyRepository keyRepo;
    @Autowired ApiKeyService keySvc;
    @Autowired EmpresaRepository empresaRepo;
    @Autowired JdbcTemplate jdbc;

    private RestClient client() { return RestClient.create("http://localhost:" + port); }

    /** Semeadura idempotente de um fixture fictício (provedores_municipais não é truncada). */
    private void semearProvedor(String ibge, String tipo, String provedor, String versao) {
        jdbc.update("delete from provedores_municipais where codigo_ibge = ?", ibge);
        jdbc.update("insert into provedores_municipais"
                + " (codigo_ibge, nome, uf, tipo, provedor, versao_abrasf, estilo_envelope, algoritmo)"
                + " values (?, 'Fixture', 'PA', ?, ?, ?, 'NFSE_DADOS_MSG', 'SHA1')",
                ibge, tipo, provedor, versao);
    }

    private record Setup(String chave, UUID empresaId) {}

    private Setup setup(String nome, String chave, Ambiente ambiente) {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta c = contaRepo.save(Conta.nova(nome, agora));
        keyRepo.save(ApiKey.nova(c.getId(), ambiente, keySvc.hash(chave), chave.substring(0, 14), agora));
        Empresa e = empresaRepo.save(Empresa.nova(c.getId(), "11222333000181", nome + " Ltda", "123",
                "4204608", OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora));
        return new Setup(chave, e.getId());
    }

    private String json(UUID empresaId, String ibge, String simular) {
        return """
            {"empresaId":"%s","codigoMunicipioIbge":"%s",
             "servico":{"valorServicos":"1500.00","itemListaServico":"01.01",
                        "discriminacao":"Consultoria em TI","issRetido":2,"exigibilidadeIss":1},
             "simular":"%s"}
            """.formatted(empresaId, ibge, simular);
    }

    private HttpStatusCode postStatus(Setup s, String ibge, String simular) {
        return client().post().uri("/v1/nfse-municipal").header("X-Api-Key", s.chave())
                .contentType(MediaType.APPLICATION_JSON).body(json(s.empresaId(), ibge, simular))
                .exchange((req, res) -> res.getStatusCode());
    }

    @Test
    void emiteMunicipalAutorizadaEConsulta() {
        Setup s = setup("Cli Mun", "sk_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", Ambiente.SANDBOX);
        semearProvedor("9999902", "ABRASF_2X", "ProvTeste", "2.04");

        var respostaEmissao = client().post().uri("/v1/nfse-municipal")
                .header("X-Api-Key", s.chave()).contentType(MediaType.APPLICATION_JSON)
                .body(json(s.empresaId(), "9999902", "AUTORIZADA"))
                .retrieve().toEntity(EmissaoMunicipalResponse.class);
        assertEquals(202, respostaEmissao.getStatusCode().value());
        EmissaoMunicipalResponse emitida = respostaEmissao.getBody();

        assertEquals("AUTORIZADA", emitida.status());
        assertThat(emitida.numeroNfse()).isNotBlank();
        assertThat(emitida.xmlEnviado()).contains("<Signature").contains("GerarNfseEnvio");

        var consulta = client().get().uri("/v1/nfse-municipal/" + emitida.id())
                .header("X-Api-Key", s.chave()).retrieve().toEntity(EmissaoMunicipalResponse.class);
        assertEquals(200, consulta.getStatusCode().value());
        assertEquals("AUTORIZADA", consulta.getBody().status());
    }

    @Test
    void ibgeAdnRetorna409() {
        Setup s = setup("Cli ADN", "sk_test_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", Ambiente.SANDBOX);
        semearProvedor("9999901", "ADN", "PadraoNacional", null);
        assertEquals(409, postStatus(s, "9999901", "AUTORIZADA").value());
    }

    @Test
    void ibgeDesconhecidoRetorna422() {
        Setup s = setup("Cli Desc", "sk_test_cccccccccccccccccccccccccccccccc", Ambiente.SANDBOX);
        assertEquals(422, postStatus(s, "9999900", "AUTORIZADA").value());
    }

    @Test
    void crossTenantConsultaRetorna404() {
        Setup a = setup("Conta A", "sk_test_dddddddddddddddddddddddddddddddd", Ambiente.SANDBOX);
        Setup b = setup("Conta B", "sk_test_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", Ambiente.SANDBOX);
        semearProvedor("9999902", "ABRASF_2X", "ProvTeste", "2.04");
        UUID idA = client().post().uri("/v1/nfse-municipal").header("X-Api-Key", a.chave())
                .contentType(MediaType.APPLICATION_JSON).body(json(a.empresaId(), "9999902", "AUTORIZADA"))
                .retrieve().toEntity(EmissaoMunicipalResponse.class).getBody().id();

        HttpStatusCode st = client().get().uri("/v1/nfse-municipal/" + idA).header("X-Api-Key", b.chave())
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(404, st.value());
    }

    @Test
    void chaveProducaoRetorna501() {
        Setup s = setup("Cli Prod", "sk_live_ffffffffffffffffffffffffffffffff", Ambiente.PRODUCAO);
        assertEquals(501, postStatus(s, "9999902", "AUTORIZADA").value());
    }
}
