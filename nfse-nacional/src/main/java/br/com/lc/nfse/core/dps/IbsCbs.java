package br.com.lc.nfse.core.dps;

/**
 * Classificação IBS/CBS da reforma tributária, para o grupo IBSCBS do DPS.
 * As alíquotas/valores NÃO vão no DPS — são apuradas pelo ADN. Aqui vai só a classificação.
 */
public record IbsCbs(String finNFSe, String cIndOp, String indDest, String cst, String cClassTrib) {

    /** Classificação padrão para homologação: NFS-e regular, tributação integral. */
    public static IbsCbs padrao() {
        return new IbsCbs("0", "000000", "0", "000", "000001");
    }
}
