package br.com.lc.nfse.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** CNPJ prestador sob uma Conta. Emite NFS-e (fatia futura). */
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    private UUID id;

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Column(nullable = false)
    private String cnpj;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "inscricao_municipal")
    private String inscricaoMunicipal;

    @Column(name = "cod_mun_ibge", nullable = false)
    private String codMunIbge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEmpresa status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Empresa() {}

    public static Empresa nova(UUID contaId, String cnpj, String razaoSocial,
                               String inscricaoMunicipal, String codMunIbge, OffsetDateTime agora) {
        Empresa e = new Empresa();
        e.id = UUID.randomUUID();
        e.contaId = contaId;
        e.cnpj = cnpj;
        e.razaoSocial = razaoSocial;
        e.inscricaoMunicipal = inscricaoMunicipal;
        e.codMunIbge = codMunIbge;
        e.status = StatusEmpresa.ATIVA;
        e.criadoEm = agora;
        e.atualizadoEm = agora;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public String getCnpj() { return cnpj; }
    public String getRazaoSocial() { return razaoSocial; }
    public String getInscricaoMunicipal() { return inscricaoMunicipal; }
    public String getCodMunIbge() { return codMunIbge; }
    public StatusEmpresa getStatus() { return status; }
}
