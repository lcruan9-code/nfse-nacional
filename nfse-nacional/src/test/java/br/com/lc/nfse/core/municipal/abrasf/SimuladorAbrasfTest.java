package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimuladorAbrasfTest {

    @Test
    void autorizada_temNumeroECodigo() {
        String resp = new SimuladorAbrasf().responder("AUTORIZADA", "10", "<GerarNfseEnvio/>");
        ResultadoEmissao r = new AbrasfRetornoParser().parse("<GerarNfseEnvio/>", resp);
        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.AUTORIZADA);
        assertThat(r.numeroNfse()).isEqualTo("10");
        assertThat(r.codigoVerificacao()).isNotBlank();
        assertThat(r.xmlRetorno()).contains("CompNfse");
    }

    @Test
    void rejeitada_temMensagem() {
        String resp = new SimuladorAbrasf().responder("REJEITADA", "10", "<GerarNfseEnvio/>");
        ResultadoEmissao r = new AbrasfRetornoParser().parse("<GerarNfseEnvio/>", resp);
        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.REJEITADA);
        assertThat(r.mensagens()).isNotEmpty();
    }
}
