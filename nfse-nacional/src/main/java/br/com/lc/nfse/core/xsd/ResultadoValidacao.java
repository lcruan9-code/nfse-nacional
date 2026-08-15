package br.com.lc.nfse.core.xsd;

/** Resultado da validação de um XML contra um XSD. Value type genérico (ADN + municipal). */
public record ResultadoValidacao(boolean valido, String mensagem) {
    public static ResultadoValidacao ok() { return new ResultadoValidacao(true, null); }
    public static ResultadoValidacao erro(String mensagem) { return new ResultadoValidacao(false, mensagem); }
}
