package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Uma emissão municipal (ABRASF), no protótipo simulada. Escopada por Conta. */
@Entity
@Table(name = "emissoes_municipais")
public class EmissaoMunicipal {

    @Id
    private UUID id;

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "codigo_ibge", nullable = false)
    private String codigoIbge;

    @Column(nullable = false)
    private String provedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEmissaoMunicipal status;

    @Column(name = "numero_nfse")
    private String numeroNfse;

    @Column(name = "codigo_verificacao")
    private String codigoVerificacao;

    private String protocolo;

    @Column
    private String mensagens;

    @Column(name = "xml_enviado")
    private String xmlEnviado;

    @Column(name = "xml_retorno")
    private String xmlRetorno;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected EmissaoMunicipal() {}

    public static EmissaoMunicipal autorizada(UUID contaId, UUID empresaId, String codigoIbge, String provedor,
                                              String numeroNfse, String codigoVerificacao, String protocolo,
                                              String xmlEnviado, String xmlRetorno, OffsetDateTime agora) {
        EmissaoMunicipal e = base(contaId, empresaId, codigoIbge, provedor, xmlEnviado, xmlRetorno, agora);
        e.status = StatusEmissaoMunicipal.AUTORIZADA;
        e.numeroNfse = numeroNfse;
        e.codigoVerificacao = codigoVerificacao;
        e.protocolo = protocolo;
        return e;
    }

    public static EmissaoMunicipal rejeitada(UUID contaId, UUID empresaId, String codigoIbge, String provedor,
                                             List<String> mensagens, String xmlEnviado, String xmlRetorno,
                                             OffsetDateTime agora) {
        EmissaoMunicipal e = base(contaId, empresaId, codigoIbge, provedor, xmlEnviado, xmlRetorno, agora);
        e.status = StatusEmissaoMunicipal.REJEITADA;
        e.mensagens = mensagens == null ? null : String.join("\n", mensagens);
        return e;
    }

    private static EmissaoMunicipal base(UUID contaId, UUID empresaId, String codigoIbge, String provedor,
                                         String xmlEnviado, String xmlRetorno, OffsetDateTime agora) {
        EmissaoMunicipal e = new EmissaoMunicipal();
        e.id = UUID.randomUUID();
        e.contaId = contaId;
        e.empresaId = empresaId;
        e.codigoIbge = codigoIbge;
        e.provedor = provedor;
        e.xmlEnviado = xmlEnviado;
        e.xmlRetorno = xmlRetorno;
        e.criadoEm = agora;
        return e;
    }

    public List<String> mensagensLista() {
        return (mensagens == null || mensagens.isBlank()) ? List.of() : List.of(mensagens.split("\n"));
    }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigoIbge() { return codigoIbge; }
    public String getProvedor() { return provedor; }
    public StatusEmissaoMunicipal getStatus() { return status; }
    public String getNumeroNfse() { return numeroNfse; }
    public String getCodigoVerificacao() { return codigoVerificacao; }
    public String getProtocolo() { return protocolo; }
    public String getXmlEnviado() { return xmlEnviado; }
    public String getXmlRetorno() { return xmlRetorno; }
}
