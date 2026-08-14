package br.com.lc.nfse.api.emissao;

import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.emissao.dto.EmissaoResponse;
import br.com.lc.nfse.api.emissao.dto.EmitirNfseRequest;
import br.com.lc.nfse.api.error.NaoEncontradoException;
import br.com.lc.nfse.api.tenant.Empresa;
import br.com.lc.nfse.api.tenant.EmpresaRepository;
import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.dps.DpsBuilder;
import br.com.lc.nfse.core.dps.DpsSigner;
import br.com.lc.nfse.core.dps.DpsValidator;
import br.com.lc.nfse.core.dps.IbsCbs;
import br.com.lc.nfse.core.dps.RequisicaoDpsDto;
import br.com.lc.nfse.core.dps.ResultadoValidacao;
import br.com.lc.nfse.web.CertificadoProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Orquestra a emissão sandbox: monta+valida+assina a DPS (core #1) e simula a autorização. */
@Service
public class EmissaoService {

    private static final DateTimeFormatter FMT_DH = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneOffset BR = ZoneOffset.of("-03:00");

    private final EmpresaRepository empresaRepository;
    private final EmissaoRepository emissaoRepository;
    private final DpsBuilder dpsBuilder;
    private final DpsValidator dpsValidator;
    private final DpsSigner dpsSigner;
    private final CertificadoProvider certificadoProvider;
    private final SimuladorNfse simulador;

    public EmissaoService(EmpresaRepository empresaRepository, EmissaoRepository emissaoRepository,
                          DpsBuilder dpsBuilder, DpsValidator dpsValidator, DpsSigner dpsSigner,
                          CertificadoProvider certificadoProvider, SimuladorNfse simulador) {
        this.empresaRepository = empresaRepository;
        this.emissaoRepository = emissaoRepository;
        this.dpsBuilder = dpsBuilder;
        this.dpsValidator = dpsValidator;
        this.dpsSigner = dpsSigner;
        this.certificadoProvider = certificadoProvider;
        this.simulador = simulador;
    }

    @Transactional
    public EmissaoResponse emitir(TenantPrincipal principal, EmitirNfseRequest req) {
        if (principal.ambiente() != Ambiente.SANDBOX) {
            throw new ProducaoIndisponivelException("Emissão em produção ainda não disponível");
        }
        Empresa empresa = empresaRepository.findByIdAndContaId(req.empresaId(), principal.contaId())
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada: " + req.empresaId()));

        String numero = String.valueOf(emissaoRepository.countByEmpresaId(empresa.getId()) + 1);
        RequisicaoDpsDto dto = montarDto(empresa, req, numero);

        String xml = dpsBuilder.construir(dto, IbsCbs.padrao());
        ResultadoValidacao validacao = dpsValidator.validar(xml);
        if (!validacao.valido()) {
            throw new IllegalArgumentException("DPS inválida: " + validacao.mensagem());
        }
        CertificadoLoader.Certificado cert = certificadoProvider.obter().orElseThrow(
                () -> new IllegalStateException("Certificado de assinatura do sandbox não configurado"));
        String assinado = dpsSigner.assinar(xml, cert);

        ResultadoSimulado sim = simulador.simular(req.simular(), numero);
        OffsetDateTime agora = OffsetDateTime.now();
        Emissao emissao;
        if (sim.status() == StatusEmissao.AUTORIZADA) {
            // Alíquotas IBS/CBS apuradas pelo "ADN" (aqui, o sandbox). Default homologação: CBS 0,90% / IBS 0,10%.
            String aliqCbs = valorOu(req.aliquotaCbs(), "0.90");
            String aliqIbs = valorOu(req.aliquotaIbs(), "0.10");
            double base = paraDouble(req.valores().valorServico());
            emissao = Emissao.autorizada(principal.contaId(), empresa.getId(), Ambiente.SANDBOX,
                    sim.chaveAcesso(), sim.numeroNfse(), assinado,
                    aliqCbs, formatar(base * paraDouble(aliqCbs) / 100.0),
                    aliqIbs, formatar(base * paraDouble(aliqIbs) / 100.0), agora);
        } else {
            emissao = Emissao.rejeitada(principal.contaId(), empresa.getId(), Ambiente.SANDBOX,
                    sim.motivo(), assinado, agora);
        }
        emissaoRepository.save(emissao);
        return toResponse(emissao);
    }

    @Transactional(readOnly = true)
    public EmissaoResponse consultar(UUID contaId, UUID id) {
        return emissaoRepository.findByIdAndContaId(id, contaId).map(this::toResponse)
                .orElseThrow(() -> new NaoEncontradoException("Emissão não encontrada: " + id));
    }

    private RequisicaoDpsDto montarDto(Empresa e, EmitirNfseRequest req, String numero) {
        OffsetDateTime agora = OffsetDateTime.now(BR);
        return new RequisicaoDpsDto(
                e.getCnpj(), e.getCodMunIbge(), "1", numero,
                agora.format(FMT_DH), agora.format(FMT_DATA),
                req.servico().codMunPrestacao(), req.servico().codTribNacional(),
                req.servico().descricao(), req.valores().valorServico(),
                e.getOpSimplesNacional().codigo(), e.getRegimeEspecialTributacao().codigo(),
                req.valores().tributacaoIssqn(), req.valores().tipoRetencaoIssqn());
    }

    private EmissaoResponse toResponse(Emissao e) {
        return new EmissaoResponse(e.getId(), e.getStatus().name(), e.getChaveAcesso(),
                e.getNumeroNfse(), e.getMotivo(), e.getXmlDps(),
                e.getAliquotaCbs(), e.getValorCbs(), e.getAliquotaIbs(), e.getValorIbs());
    }

    private static String valorOu(String v, String padrao) {
        return (v == null || v.isBlank()) ? padrao : v.trim();
    }

    private static double paraDouble(String v) {
        try {
            return Double.parseDouble(v.replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String formatar(double d) {
        return String.format(java.util.Locale.US, "%.2f", d);
    }
}
