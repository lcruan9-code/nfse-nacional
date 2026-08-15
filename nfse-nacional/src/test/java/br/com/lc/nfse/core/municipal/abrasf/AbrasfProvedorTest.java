package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.AlgoritmoAssinatura;
import br.com.lc.nfse.core.municipal.EstiloEnvelope;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.RpsRequest;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import br.com.lc.nfse.core.municipal.TipoProvedor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfProvedorTest {

    private ProvedorConfig cfg() {
        return new ProvedorConfig(TipoProvedor.ABRASF_2X, "2.04", "https://hom/ws", "https://prod/ws",
                EstiloEnvelope.NFSE_DADOS_MSG, AlgoritmoAssinatura.SHA1);
    }

    private RpsRequest rps() {
        return new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"), LocalDate.parse("2026-08-14"),
                "1500.00", 2, "01.01", "Consultoria", "4204608", 1, "11222333000181", "123", 2, 2);
    }

    @Test
    void emiteAutorizadaComXmlAssinado() throws Exception {
        CertificadoLoader.Certificado cert = new CertificadoLoader().carregar(
                getClass().getResourceAsStream("/certs/teste.p12"), "changeit".toCharArray());
        ResultadoEmissao r = new AbrasfProvedor().emitir(rps(), cert, cfg(), "AUTORIZADA");

        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.AUTORIZADA);
        assertThat(r.numeroNfse()).isEqualTo("1");
        assertThat(r.xmlEnviado()).contains("<Signature").contains("<GerarNfseEnvio");
    }

    @Test
    void tipoEhAbrasf() {
        assertThat(new AbrasfProvedor().tipo()).isEqualTo(TipoProvedor.ABRASF_2X);
    }
}
