package br.com.lc.nfse.core.dps;

/** Erro ao assinar a DPS (XMLDSig). */
public class AssinaturaException extends RuntimeException {
    public AssinaturaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
