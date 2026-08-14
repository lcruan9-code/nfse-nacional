package br.com.lc.nfse.api.web;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Ecoa a identidade resolvida — prova a autenticação e o ambiente da chave. */
@RestController
public class WhoamiController {

    @GetMapping("/v1/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal TenantPrincipal principal) {
        return Map.of(
                "contaId", principal.contaId(),
                "nomeConta", principal.nomeConta(),
                "ambiente", principal.ambiente());
    }
}
