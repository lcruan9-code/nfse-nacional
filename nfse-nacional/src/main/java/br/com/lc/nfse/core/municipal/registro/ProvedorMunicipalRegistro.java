package br.com.lc.nfse.core.municipal.registro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "provedores_municipais")
public class ProvedorMunicipalRegistro {

    @Id
    @Column(name = "codigo_ibge")
    private String codigoIbge;

    private String nome;
    private String uf;
    private String tipo;

    @Column(name = "versao_abrasf")
    private String versaoAbrasf;

    @Column(name = "url_homolog")
    private String urlHomolog;

    @Column(name = "url_prod")
    private String urlProd;

    @Column(name = "estilo_envelope")
    private String estiloEnvelope;

    private String algoritmo;

    protected ProvedorMunicipalRegistro() {}

    public String getCodigoIbge() { return codigoIbge; }
    public String getNome() { return nome; }
    public String getUf() { return uf; }
    public String getTipo() { return tipo; }
    public String getVersaoAbrasf() { return versaoAbrasf; }
    public String getUrlHomolog() { return urlHomolog; }
    public String getUrlProd() { return urlProd; }
    public String getEstiloEnvelope() { return estiloEnvelope; }
    public String getAlgoritmo() { return algoritmo; }
}
