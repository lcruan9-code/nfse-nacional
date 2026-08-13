package br.com.lc.nfse.core.dps;

/** Resultado da validação de uma DPS contra o XSD oficial. */
public record ResultadoValidacao(boolean valido, String mensagem) {

    public static ResultadoValidacao ok() {
        return new ResultadoValidacao(true, null);
    }

    public static ResultadoValidacao erro(String mensagem) {
        return new ResultadoValidacao(false, mensagem);
    }
}
