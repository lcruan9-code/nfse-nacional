package br.com.lc.nfse.api.emissao;

/** Emissão em produção ainda não disponível (só sandbox nesta fatia). */
public class ProducaoIndisponivelException extends RuntimeException {
    public ProducaoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
