package br.com.lc.nfse.api.emissao;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.emissao.dto.EmissaoResponse;
import br.com.lc.nfse.api.emissao.dto.EmitirNfseRequest;
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

/** Emissão de NFS-e (sandbox). 202 = aceito; consulta o status por id. */
@RestController
@RequestMapping("/v1/nfse")
public class EmissaoController {

    private final EmissaoService service;

    public EmissaoController(EmissaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmissaoResponse> emitir(@AuthenticationPrincipal TenantPrincipal principal,
                                                  @RequestBody EmitirNfseRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.emitir(principal, req));
    }

    @GetMapping("/{id}")
    public EmissaoResponse consultar(@AuthenticationPrincipal TenantPrincipal principal,
                                     @PathVariable UUID id) {
        return service.consultar(principal.contaId(), id);
    }
}
