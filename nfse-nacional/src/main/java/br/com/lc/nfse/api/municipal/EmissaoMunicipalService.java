package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.emissao.ProducaoIndisponivelException;
import br.com.lc.nfse.api.error.ConflitoException;
import br.com.lc.nfse.api.error.NaoEncontradoException;
import br.com.lc.nfse.api.municipal.dto.EmissaoMunicipalResponse;
import br.com.lc.nfse.api.municipal.dto.EmitirNfseMunicipalRequest;
import br.com.lc.nfse.api.tenant.Empresa;
import br.com.lc.nfse.api.tenant.EmpresaRepository;
import br.com.lc.nfse.api.tenant.OpSimplesNacional;
import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.FabricaProvedor;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.RpsRequest;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import br.com.lc.nfse.core.municipal.abrasf.AbrasfProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolucaoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolvedorProvedor;
import br.com.lc.nfse.web.CertificadoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Orquestra a emissão municipal: resolve por IBGE -> fabrica provedor -> emite -> persiste. */
@Service
public class EmissaoMunicipalService {

    private static final Logger log = LoggerFactory.getLogger(EmissaoMunicipalService.class);
    private static final ZoneOffset BR = ZoneOffset.of("-03:00");

    private final EmpresaRepository empresaRepository;
    private final EmissaoMunicipalRepository emissaoRepository;
    private final ResolvedorProvedor resolvedor;
    private final FabricaProvedor fabrica;
    private final CertificadoProvider certificadoProvider;

    public EmissaoMunicipalService(EmpresaRepository empresaRepository,
                                   EmissaoMunicipalRepository emissaoRepository,
                                   ResolvedorProvedor resolvedor, FabricaProvedor fabrica,
                                   CertificadoProvider certificadoProvider) {
        this.empresaRepository = empresaRepository;
        this.emissaoRepository = emissaoRepository;
        this.resolvedor = resolvedor;
        this.fabrica = fabrica;
        this.certificadoProvider = certificadoProvider;
    }

    @Transactional
    public EmissaoMunicipalResponse emitir(TenantPrincipal principal, EmitirNfseMunicipalRequest req) {
        if (principal.ambiente() != Ambiente.SANDBOX) {
            throw new ProducaoIndisponivelException("Emissão municipal em produção ainda não disponível");
        }
        ProvedorConfig cfg = resolverConfig(req.codigoMunicipioIbge());

        Empresa empresa = empresaRepository.findByIdAndContaId(req.empresaId(), principal.contaId())
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada: " + req.empresaId()));

        String numero = String.valueOf(emissaoRepository.countByEmpresaId(empresa.getId()) + 1);
        RpsRequest rps = montarRps(empresa, req, numero);

        CertificadoLoader.Certificado cert = certificadoProvider.obter().orElseThrow(
                () -> new IllegalStateException("Certificado de assinatura do sandbox não configurado"));

        AbrasfProvedor provedor = (AbrasfProvedor) fabrica.criar(cfg.tipo());
        ResultadoEmissao r = provedor.emitir(rps, cert, cfg, req.simular());

        OffsetDateTime agora = OffsetDateTime.now();
        EmissaoMunicipal emissao = r.status() == StatusEmissaoMunicipal.AUTORIZADA
                ? EmissaoMunicipal.autorizada(principal.contaId(), empresa.getId(), req.codigoMunicipioIbge(),
                    cfg.tipo().name(), r.numeroNfse(), r.codigoVerificacao(), r.protocolo(),
                    r.xmlEnviado(), r.xmlRetorno(), agora)
                : EmissaoMunicipal.rejeitada(principal.contaId(), empresa.getId(), req.codigoMunicipioIbge(),
                    cfg.tipo().name(), r.mensagens(), r.xmlEnviado(), r.xmlRetorno(), agora);
        emissaoRepository.save(emissao);
        return EmissaoMunicipalResponse.de(emissao);
    }

    @Transactional(readOnly = true)
    public EmissaoMunicipalResponse consultar(UUID contaId, UUID id) {
        return emissaoRepository.findByIdAndContaId(id, contaId).map(EmissaoMunicipalResponse::de)
                .orElseThrow(() -> new NaoEncontradoException("Emissão não encontrada: " + id));
    }

    /** Java 17 não tem pattern matching em switch (preview) — instanceof encadeado no lugar. */
    private ProvedorConfig resolverConfig(String ibge) {
        ResolucaoProvedor resolucao = resolvedor.resolver(ibge);
        if (resolucao instanceof ResolucaoProvedor.ProvedorResolvido pr) {
            return pr.config();
        }
        if (resolucao instanceof ResolucaoProvedor.CidadeAdn) {
            throw new ConflitoException("Cidade é Padrão Nacional (ADN); use POST /v1/nfse");
        }
        ResolucaoProvedor.CidadeNaoSuportada ns = (ResolucaoProvedor.CidadeNaoSuportada) resolucao;
        log.info("IBGE não suportado solicitado: {} (provedor conhecido: {})", ns.ibge(), ns.provedor()); // sinal de demanda
        throw new IllegalArgumentException("Município ainda não suportado: " + ns.ibge());
    }

    private RpsRequest montarRps(Empresa e, EmitirNfseMunicipalRequest req, String numero) {
        LocalDate hoje = OffsetDateTime.now(BR).toLocalDate();
        int optante = e.getOpSimplesNacional() == OpSimplesNacional.NAO_OPTANTE ? 2 : 1;
        var s = req.servico();
        return new RpsRequest(numero, "1", "1", hoje, hoje,
                s.valorServicos(), s.issRetido(), s.itemListaServico(), s.discriminacao(),
                req.codigoMunicipioIbge(), s.exigibilidadeIss(),
                e.getCnpj(), e.getInscricaoMunicipal() == null ? "0" : e.getInscricaoMunicipal(),
                optante, 2);
    }
}
