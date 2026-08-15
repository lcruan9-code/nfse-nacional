package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.EstiloEnvelope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfSoapEnvelopeTest {

    @Test
    void envelopaComNfseDadosMsg() {
        String env = new AbrasfSoapEnvelope().envelopar("<GerarNfseEnvio/>", EstiloEnvelope.NFSE_DADOS_MSG);
        assertThat(env).contains("soap:Envelope").contains("GerarNfse").contains("<GerarNfseEnvio/>");
    }
}
