package br.com.lc.nfse.api.error;

/** Envelope de erro consistente da API: { "erro": { "codigo", "mensagem" } }. */
public record EnvelopeErro(Erro erro) {

    public record Erro(String codigo, String mensagem) {}

    public static EnvelopeErro de(String codigo, String mensagem) {
        return new EnvelopeErro(new Erro(codigo, mensagem));
    }
}
