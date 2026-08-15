package br.com.lc.nfse.api.municipal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmissaoMunicipalRepository extends JpaRepository<EmissaoMunicipal, UUID> {
    Optional<EmissaoMunicipal> findByIdAndContaId(UUID id, UUID contaId);
    long countByEmpresaId(UUID empresaId);
}
