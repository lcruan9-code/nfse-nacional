package br.com.lc.nfse.api.emissao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimuladorNfseTest {

    private final SimuladorNfse sim = new SimuladorNfse();

    @Test
    void defaultAutoriza() {
        ResultadoSimulado r = sim.simular(null, "1");
        assertEquals(StatusEmissao.AUTORIZADA, r.status());
        assertEquals(50, r.chaveAcesso().length());
        assertTrue(r.chaveAcesso().chars().allMatch(Character::isDigit));
        assertNull(r.motivo());
    }

    @Test
    void simularRejeitadaRejeita() {
        ResultadoSimulado r = sim.simular("REJEITADA", "1");
        assertEquals(StatusEmissao.REJEITADA, r.status());
        assertNull(r.chaveAcesso());
        assertTrue(r.motivo() != null && !r.motivo().isBlank());
    }

    @Test
    void simularDesconhecidoLancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> sim.simular("XPTO", "1"));
    }
}
