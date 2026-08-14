package br.com.lc.nfse.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tenant que consome a API. Sob ela ficam as Empresas (CNPJs prestadores). */
@Entity
@Table(name = "contas")
public class Conta {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConta status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Conta() {}

    public static Conta nova(String nome, OffsetDateTime agora) {
        Conta c = new Conta();
        c.id = UUID.randomUUID();
        c.nome = nome;
        c.status = StatusConta.ATIVA;
        c.criadoEm = agora;
        c.atualizadoEm = agora;
        return c;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public StatusConta getStatus() { return status; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
