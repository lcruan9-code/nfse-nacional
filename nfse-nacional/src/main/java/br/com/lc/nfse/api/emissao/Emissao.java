package br.com.lc.nfse.api.emissao;

import br.com.lc.nfse.api.auth.Ambiente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma emissão de NFS-e (no protótipo, simulada no sandbox). Escopada por Conta. */
@Entity
@Table(name = "emissoes")
public class Emissao {

    @Id
    private UUID id;

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ambiente ambiente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEmissao status;

    @Column(name = "chave_acesso")
    private String chaveAcesso;

    @Column(name = "numero_nfse")
    private String numeroNfse;

    @Column(name = "xml_dps")
    private String xmlDps;

    @Column
    private String motivo;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Emissao() {}

    public static Emissao autorizada(UUID contaId, UUID empresaId, Ambiente ambiente, String chaveAcesso,
                                     String numeroNfse, String xmlDps, OffsetDateTime agora) {
        Emissao e = base(contaId, empresaId, ambiente, xmlDps, agora);
        e.status = StatusEmissao.AUTORIZADA;
        e.chaveAcesso = chaveAcesso;
        e.numeroNfse = numeroNfse;
        return e;
    }

    public static Emissao rejeitada(UUID contaId, UUID empresaId, Ambiente ambiente, String motivo,
                                    String xmlDps, OffsetDateTime agora) {
        Emissao e = base(contaId, empresaId, ambiente, xmlDps, agora);
        e.status = StatusEmissao.REJEITADA;
        e.motivo = motivo;
        return e;
    }

    private static Emissao base(UUID contaId, UUID empresaId, Ambiente ambiente, String xmlDps,
                                OffsetDateTime agora) {
        Emissao e = new Emissao();
        e.id = UUID.randomUUID();
        e.contaId = contaId;
        e.empresaId = empresaId;
        e.ambiente = ambiente;
        e.xmlDps = xmlDps;
        e.criadoEm = agora;
        e.atualizadoEm = agora;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public UUID getEmpresaId() { return empresaId; }
    public Ambiente getAmbiente() { return ambiente; }
    public StatusEmissao getStatus() { return status; }
    public String getChaveAcesso() { return chaveAcesso; }
    public String getNumeroNfse() { return numeroNfse; }
    public String getXmlDps() { return xmlDps; }
    public String getMotivo() { return motivo; }
}
