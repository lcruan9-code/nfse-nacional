package br.com.lc.nfse.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContaRepository extends JpaRepository<Conta, UUID> {
}
