package br.com.lc.nfse.api.tenant;

/** Apuração do ISS no Simples Nacional (Spedy simplesNacionalTaxRegime → DPS regApTribSN). */
public enum RegimeApuracaoSimplesNacional {
    FEDERAL_MUNICIPAL_SN(1), FEDERAL_SN_ISSQN_NFSE(2), FEDERAL_MUNICIPAL_NFSE(3);

    private final int codigo;

    RegimeApuracaoSimplesNacional(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }
}
