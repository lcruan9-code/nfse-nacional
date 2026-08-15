package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.RpsRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbrasfXmlBuilderTest {

    private RpsRequest rps() {
        return new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"), LocalDate.parse("2026-08-14"),
                "1500.00", 2, "01.01", "Consultoria em TI", "4204608", 1,
                "11222333000181", "123", 2, 2);
    }

    @Test
    void montaXmlQueValidaNoXsd() {
        String xml = new AbrasfXmlBuilder().montar(rps(), "2.04", "rps1");
        assertThat(xml).contains("<GerarNfseEnvio").contains("Id=\"rps1\"");
        assertThat(new AbrasfValidator().validar(xml).valido())
                .as("XML deve validar no XSD ABRASF 2.04").isTrue();
    }

    @Test
    void versaoNaoSuportada_lanca() {
        assertThatThrownBy(() -> new AbrasfXmlBuilder().montar(rps(), "1.00", "rps1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
