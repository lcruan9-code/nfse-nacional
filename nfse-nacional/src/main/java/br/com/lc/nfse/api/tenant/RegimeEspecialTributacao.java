package br.com.lc.nfse.api.tenant;

/** Regime especial de tributação do ISS (Spedy specialTaxRegime → DPS regEspTrib). */
public enum RegimeEspecialTributacao {
    NENHUM(0), COOPERATIVA(1), ESTIMATIVA(2), MICRO_MUNICIPAL(3),
    NOTARIO(4), PROF_AUTONOMO(5), SOCIEDADE_PROFISSIONAIS(6), OUTROS(9);

    private final int codigo;

    RegimeEspecialTributacao(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }
}
