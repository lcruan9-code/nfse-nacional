package br.com.lc.nfse.core.dps;

import br.com.lc.nfse.core.xsd.ResultadoValidacao;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DpsValidatorTest {

    private final DpsValidator validator = new DpsValidator();

    @Test
    void dpsIncompletaFalhaComMensagem() {
        // infDPS sem os filhos obrigatórios e com Id fora do padrão -> inválido
        String xml = "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.00\">"
                + "<infDPS Id=\"X\"></infDPS></DPS>";

        ResultadoValidacao r = validator.validar(xml);

        assertFalse(r.valido());
        assertNotNull(r.mensagem());
    }
}
