package br.com.lc.nfse.core.cert;

/** Erro ao carregar/usar o certificado digital. */
public class CertificadoException extends RuntimeException {
    public CertificadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
