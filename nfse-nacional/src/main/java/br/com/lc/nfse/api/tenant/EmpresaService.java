package br.com.lc.nfse.api.tenant;

import br.com.lc.nfse.api.error.ConflitoException;
import br.com.lc.nfse.api.error.NaoEncontradoException;
import br.com.lc.nfse.api.web.dto.CriarEmpresaRequest;
import br.com.lc.nfse.api.web.dto.EmpresaResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Regras de Empresa, sempre escopadas pela Conta autenticada. */
@Service
public class EmpresaService {

    private static final Pattern CNPJ = Pattern.compile("\\d{14}");
    private static final Pattern IBGE = Pattern.compile("\\d{7}");

    private final EmpresaRepository repositorio;

    public EmpresaService(EmpresaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public EmpresaResponse criar(UUID contaId, CriarEmpresaRequest req) {
        validar(req);
        if (repositorio.existsByContaIdAndCnpj(contaId, req.cnpj())) {
            throw new ConflitoException("CNPJ já cadastrado nesta conta: " + req.cnpj());
        }
        RegimeEspecialTributacao especial = req.regimeEspecialTributacao() != null
                ? req.regimeEspecialTributacao() : RegimeEspecialTributacao.NENHUM;
        Empresa e = repositorio.save(Empresa.nova(contaId, req.cnpj(), req.razaoSocial(),
                req.inscricaoMunicipal(), req.codMunIbge(), req.opSimplesNacional(), especial,
                req.regimeApuracaoSimplesNacional(), OffsetDateTime.now()));
        return toResponse(e);
    }

    public List<EmpresaResponse> listar(UUID contaId) {
        return repositorio.findByContaId(contaId).stream().map(this::toResponse).toList();
    }

    public EmpresaResponse buscar(UUID contaId, UUID id) {
        return repositorio.findByIdAndContaId(id, contaId)
                .map(this::toResponse)
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada: " + id));
    }

    private void validar(CriarEmpresaRequest req) {
        if (req.cnpj() == null || !CNPJ.matcher(req.cnpj()).matches()) {
            throw new IllegalArgumentException("CNPJ deve ter 14 dígitos");
        }
        if (req.codMunIbge() == null || !IBGE.matcher(req.codMunIbge()).matches()) {
            throw new IllegalArgumentException("Código IBGE deve ter 7 dígitos");
        }
        if (req.razaoSocial() == null || req.razaoSocial().isBlank()) {
            throw new IllegalArgumentException("Razão social é obrigatória");
        }
        if (req.opSimplesNacional() == null) {
            throw new IllegalArgumentException("opSimplesNacional é obrigatório");
        }
    }

    private EmpresaResponse toResponse(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getCnpj(), e.getRazaoSocial(),
                e.getCodMunIbge(), e.getStatus().name());
    }
}
