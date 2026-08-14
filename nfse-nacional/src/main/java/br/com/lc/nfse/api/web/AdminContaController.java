package br.com.lc.nfse.api.web;

import br.com.lc.nfse.api.tenant.ProvisionamentoService;
import br.com.lc.nfse.api.web.dto.ContaCriadaResponse;
import br.com.lc.nfse.api.web.dto.CriarContaRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Provisionamento administrativo: protegido por X-Admin-Key (config nfse.admin.api-key). */
@RestController
public class AdminContaController {

    private final ProvisionamentoService provisionamento;
    private final String adminKey;

    public AdminContaController(ProvisionamentoService provisionamento,
                                @Value("${nfse.admin.api-key}") String adminKey) {
        this.provisionamento = provisionamento;
        this.adminKey = adminKey;
    }

    @PostMapping("/admin/contas")
    public ResponseEntity<ContaCriadaResponse> criar(
            @RequestHeader(value = "X-Admin-Key", required = false) String headerKey,
            @RequestBody CriarContaRequest req) {
        if (!chaveAdminValida(headerKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Admin-Key inválida");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(provisionamento.criarConta(req.nome()));
    }

    private boolean chaveAdminValida(String fornecida) {
        if (fornecida == null) {
            return false;
        }
        return MessageDigest.isEqual(
                fornecida.getBytes(StandardCharsets.UTF_8),
                adminKey.getBytes(StandardCharsets.UTF_8));
    }
}
