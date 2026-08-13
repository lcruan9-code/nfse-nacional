package br.com.lc.nfse.web;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.dps.DpsBuilder;
import br.com.lc.nfse.core.dps.DpsPackager;
import br.com.lc.nfse.core.dps.DpsSigner;
import br.com.lc.nfse.core.dps.DpsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fatia do controller via standalone MockMvc (sem contexto Spring/autoconfig).
 * O {@link CertificadoProvider} aponta para o certificado de teste no classpath,
 * então o pipeline completo é exercitado — inclusive assinatura e empacotamento.
 */
class DpsPreviewControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DpsPreviewController(
                    new DpsBuilder(), new DpsValidator(), new DpsSigner(), new DpsPackager(),
                    new CertificadoProvider("classpath:certs/teste.p12", "changeit", new CertificadoLoader())))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static final String DPS_VALIDA = """
            { "cnpjPrestador":"11222333000181", "codMunEmissor":"3550308", "serie":"1", "numero":"1",
              "dhEmiUtc":"2026-08-13T12:00:00-03:00", "dataCompetencia":"2026-08-13",
              "codMunPrestacao":"3550308", "codTribNacional":"010101",
              "descricaoServico":"Servico de teste", "valorServico":"100.00",
              "opSimplesNacional":1, "regimeEspecial":0, "tributacaoIssqn":1, "tipoRetencaoIssqn":1 }
            """;

    private static final String DPS_INVALIDA = """
            { "cnpjPrestador":"11222333000181", "codMunEmissor":"123", "serie":"1", "numero":"1",
              "dhEmiUtc":"2026-08-13T12:00:00-03:00", "dataCompetencia":"2026-08-13",
              "codMunPrestacao":"3550308", "codTribNacional":"010101",
              "descricaoServico":"Servico de teste", "valorServico":"100.00",
              "opSimplesNacional":1, "regimeEspecial":0, "tributacaoIssqn":1, "tipoRetencaoIssqn":1 }
            """;

    @Test
    void dtoValidoRetorna200AssinadoEEmpacotado() throws Exception {
        mockMvc.perform(post("/dps/preview").contentType(MediaType.APPLICATION_JSON).content(DPS_VALIDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.assinado").value(true))
                .andExpect(jsonPath("$.pacoteGzipB64").isNotEmpty());
    }

    @Test
    void dtoInvalidoRetorna422() throws Exception {
        mockMvc.perform(post("/dps/preview").contentType(MediaType.APPLICATION_JSON).content(DPS_INVALIDA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.valido").value(false));
    }
}
