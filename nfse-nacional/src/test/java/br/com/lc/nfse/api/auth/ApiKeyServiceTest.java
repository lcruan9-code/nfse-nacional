package br.com.lc.nfse.api.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyServiceTest {

    private final ApiKeyService svc = new ApiKeyService();

    @Test
    void gerarSandboxTemPrefixoEHashConsistente() {
        ChaveGerada c = svc.gerar(Ambiente.SANDBOX);

        assertTrue(c.textoIntegral().startsWith("sk_test_"));
        assertEquals(Ambiente.SANDBOX, c.ambiente());
        assertEquals(svc.hash(c.textoIntegral()), c.hash());
        assertEquals(64, c.hash().length()); // SHA-256 em hex
        assertTrue(c.textoIntegral().startsWith(c.prefixo()));
    }

    @Test
    void gerarProducaoUsaPrefixoLive() {
        assertTrue(svc.gerar(Ambiente.PRODUCAO).textoIntegral().startsWith("sk_live_"));
    }

    @Test
    void chavesGeradasSaoUnicas() {
        assertNotEquals(svc.gerar(Ambiente.SANDBOX).textoIntegral(),
                svc.gerar(Ambiente.SANDBOX).textoIntegral());
    }

    @Test
    void ambienteDeReconhecePeloPrefixo() {
        assertEquals(Ambiente.SANDBOX, svc.ambienteDe("sk_test_abc").orElseThrow());
        assertEquals(Ambiente.PRODUCAO, svc.ambienteDe("sk_live_abc").orElseThrow());
        assertTrue(svc.ambienteDe("xyz").isEmpty());
    }
}
