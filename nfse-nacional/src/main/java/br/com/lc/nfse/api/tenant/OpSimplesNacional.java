package br.com.lc.nfse.api.tenant;

/** Regime tributário federal (Spedy taxRegime → DPS opSimpNac). */
public enum OpSimplesNacional {
    NAO_OPTANTE(1), MEI(2), ME_EPP(3);

    private final int codigo;

    OpSimplesNacional(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }
}
