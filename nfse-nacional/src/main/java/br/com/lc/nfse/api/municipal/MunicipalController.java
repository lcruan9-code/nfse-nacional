package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.municipal.dto.EmissaoMunicipalResponse;
import br.com.lc.nfse.api.municipal.dto.EmitirNfseMunicipalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Emissão de NFS-e municipal (legado / fora do ADN). Rota separada do Padrão Nacional. */
@RestController
@RequestMapping("/v1/nfse-municipal")
public class MunicipalController {

    private final EmissaoMunicipalService service;

    public MunicipalController(EmissaoMunicipalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmissaoMunicipalResponse> emitir(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestBody EmitirNfseMunicipalRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.emitir(principal, req));
    }

    @GetMapping("/{id}")
    public EmissaoMunicipalResponse consultar(@AuthenticationPrincipal TenantPrincipal principal,
                                              @PathVariable UUID id) {
        return service.consultar(principal.contaId(), id);
    }
}
