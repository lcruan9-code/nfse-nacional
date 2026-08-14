package br.com.lc.nfse.api.emissao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmissaoRepository extends JpaRepository<Emissao, UUID> {

    Optional<Emissao> findByIdAndContaId(UUID id, UUID contaId);

    long countByEmpresaId(UUID empresaId);
}
