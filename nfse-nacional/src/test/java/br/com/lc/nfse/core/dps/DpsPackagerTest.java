package br.com.lc.nfse.core.dps;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DpsPackagerTest {

    private final DpsPackager packager = new DpsPackager();

    @Test
    void roundTripPreservaOXml() {
        String xml = "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\">"
                + "<infDPS Id=\"DPS35...\"><serie>1</serie></infDPS></DPS>";

        String pacote = packager.empacotar(xml);

        assertEquals(xml, packager.desempacotar(pacote));
    }

    @Test
    void saidaEhBase64ValidoENaoVazio() {
        String pacote = packager.empacotar("<x/>");

        assertFalse(pacote.isBlank());
        assertDoesNotThrow(() -> Base64.getDecoder().decode(pacote));
    }
}
