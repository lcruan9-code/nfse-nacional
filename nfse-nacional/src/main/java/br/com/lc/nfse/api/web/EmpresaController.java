package br.com.lc.nfse.api.web;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.tenant.EmpresaService;
import br.com.lc.nfse.api.web.dto.CriarEmpresaRequest;
import br.com.lc.nfse.api.web.dto.EmpresaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Empresas da Conta autenticada. Tudo escopado por {@code contaId} do principal. */
@RestController
@RequestMapping("/v1/empresas")
public class EmpresaController {

    private final EmpresaService service;

    public EmpresaController(EmpresaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> criar(@AuthenticationPrincipal TenantPrincipal principal,
                                                 @RequestBody CriarEmpresaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(principal.contaId(), req));
    }

    @GetMapping
    public List<EmpresaResponse> listar(@AuthenticationPrincipal TenantPrincipal principal) {
        return service.listar(principal.contaId());
    }

    @GetMapping("/{id}")
    public EmpresaResponse buscar(@AuthenticationPrincipal TenantPrincipal principal,
                                  @PathVariable UUID id) {
        return service.buscar(principal.contaId(), id);
    }
}
