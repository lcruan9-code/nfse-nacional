package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmissaoMunicipalRepositoryTest extends AbstractPostgresIT {

    @Autowired EmissaoMunicipalRepository repo;

    @Test
    void salvaEBuscaEscopadaPorConta() {
        UUID conta = UUID.randomUUID();
        UUID empresa = UUID.randomUUID();
        EmissaoMunicipal e = EmissaoMunicipal.autorizada(conta, empresa, "4204608", "ABRASF_2X",
                "1", "ABC123", null, "<xml/>", "<resp/>", OffsetDateTime.now());
        repo.save(e);

        assertThat(repo.findByIdAndContaId(e.getId(), conta)).isPresent();
        assertThat(repo.findByIdAndContaId(e.getId(), UUID.randomUUID())).isEmpty();
        assertThat(repo.countByEmpresaId(empresa)).isEqualTo(1);
    }

    @Test
    void rejeitadaGuardaMensagens() {
        UUID conta = UUID.randomUUID();
        EmissaoMunicipal e = EmissaoMunicipal.rejeitada(conta, UUID.randomUUID(), "4204608", "ABRASF_2X",
                List.of("E9999: rejeição"), "<xml/>", "<resp/>", OffsetDateTime.now());
        repo.save(e);
        assertThat(repo.findByIdAndContaId(e.getId(), conta)).get()
                .extracting(EmissaoMunicipal::getStatus).isEqualTo(StatusEmissaoMunicipal.REJEITADA);
    }
}
