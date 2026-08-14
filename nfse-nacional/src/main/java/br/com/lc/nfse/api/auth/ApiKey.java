package br.com.lc.nfse.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Chave de API de uma Conta, escopada por ambiente. Guarda só o hash (nunca o texto). */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ambiente ambiente;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusApiKey status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "ultimo_uso_em")
    private OffsetDateTime ultimoUsoEm;

    protected ApiKey() {}

    public static ApiKey nova(UUID contaId, Ambiente ambiente, String keyHash, String keyPrefix,
                              OffsetDateTime agora) {
        ApiKey k = new ApiKey();
        k.id = UUID.randomUUID();
        k.contaId = contaId;
        k.ambiente = ambiente;
        k.keyHash = keyHash;
        k.keyPrefix = keyPrefix;
        k.status = StatusApiKey.ATIVA;
        k.criadoEm = agora;
        return k;
    }

    public void revogar() { this.status = StatusApiKey.REVOGADA; }

    public void marcarUso(OffsetDateTime quando) { this.ultimoUsoEm = quando; }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public Ambiente getAmbiente() { return ambiente; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public StatusApiKey getStatus() { return status; }
    public OffsetDateTime getUltimoUsoEm() { return ultimoUsoEm; }
}
