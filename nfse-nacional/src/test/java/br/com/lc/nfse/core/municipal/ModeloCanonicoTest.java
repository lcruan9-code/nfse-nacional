package br.com.lc.nfse.core.municipal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModeloCanonicoTest {

    @Test
    void constroiModeloCanonico() {
        ProvedorConfig cfg = new ProvedorConfig(TipoProvedor.ABRASF_2X, "2.04",
                "https://hom.exemplo/ws", "https://prod.exemplo/ws",
                EstiloEnvelope.NFSE_DADOS_MSG, AlgoritmoAssinatura.SHA1);
        RpsRequest rps = new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"),
                LocalDate.parse("2026-08-14"), "1500.00", 2, "01.01", "Consultoria",
                "4204608", 1, "11222333000181", "123", 2, 2);
        ResultadoEmissao r = new ResultadoEmissao(StatusEmissaoMunicipal.AUTORIZADA, "10", "ABC123",
                "P1", "<xml/>", "<resp/>", List.of());

        assertThat(cfg.algoritmo()).isEqualTo(AlgoritmoAssinatura.SHA1);
        assertThat(rps.codigoMunicipioIbge()).isEqualTo("4204608");
        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.AUTORIZADA);
    }
}
