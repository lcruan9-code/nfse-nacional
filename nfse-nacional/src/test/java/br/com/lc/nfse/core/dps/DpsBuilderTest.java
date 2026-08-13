package br.com.lc.nfse.core.dps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DpsBuilderTest {

    private final DpsBuilder builder = new DpsBuilder();
    private final DpsValidator validator = new DpsValidator();

    @Test
    void dpsMinimaValidaContraOXsdOficial() {
        String xml = builder.construir(fixture());

        ResultadoValidacao r = validator.validar(xml);

        assertTrue(r.valido(), "DPS deveria validar contra o XSD; erro: " + r.mensagem());
    }

    static RequisicaoDpsDto fixture() {
        return new RequisicaoDpsDto(
                "11222333000181",             // cnpjPrestador
                "3550308",                    // codMunEmissor (São Paulo)
                "1",                          // serie
                "1",                          // numero
                "2026-08-13T12:00:00-03:00",  // dhEmiUtc
                "2026-08-13",                 // dataCompetencia
                "3550308",                    // codMunPrestacao
                "010101",                     // codTribNacional (6 dígitos)
                "Servico de teste de emissao NFS-e Nacional",
                "100.00",                     // valorServico
                1,                            // opSimplesNacional (Não optante)
                0,                            // regimeEspecial (Nenhum)
                1,                            // tributacaoIssqn (Operação tributável)
                1                             // tipoRetencaoIssqn (Não retido)
        );
    }
}
