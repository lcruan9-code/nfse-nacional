package br.com.lc.nfse.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {

    List<Empresa> findByContaId(UUID contaId);

    Optional<Empresa> findByIdAndContaId(UUID id, UUID contaId);

    boolean existsByContaIdAndCnpj(UUID contaId, String cnpj);
}
