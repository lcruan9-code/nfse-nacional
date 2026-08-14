package br.com.lc.nfse.api.tenant;

import br.com.lc.nfse.api.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpresaRepositoryTest extends AbstractPostgresIT {

    @Autowired
    ContaRepository contaRepository;
    @Autowired
    EmpresaRepository empresaRepository;

    @Test
    void escopaPorContaEDetectaCnpjDuplicado() {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta a = contaRepository.save(Conta.nova("Conta A", agora));
        Conta b = contaRepository.save(Conta.nova("Conta B", agora));
        empresaRepository.save(Empresa.nova(a.getId(), "11222333000181", "Emp A1", null, "3550308",
                OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora));
        empresaRepository.save(Empresa.nova(a.getId(), "11222333000262", "Emp A2", null, "3550308",
                OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora));
        Empresa daB = empresaRepository.save(Empresa.nova(b.getId(), "99888777000166", "Emp B1", null,
                "3304557", OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora));

        assertEquals(2, empresaRepository.findByContaId(a.getId()).size());
        assertTrue(empresaRepository.findByIdAndContaId(daB.getId(), a.getId()).isEmpty(), "isolamento");
        assertTrue(empresaRepository.findByIdAndContaId(daB.getId(), b.getId()).isPresent());
        assertTrue(empresaRepository.existsByContaIdAndCnpj(a.getId(), "11222333000181"));
        assertFalse(empresaRepository.existsByContaIdAndCnpj(b.getId(), "11222333000181"));
    }
}
