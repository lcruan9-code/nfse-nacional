package br.com.lc.nfse.core.municipal;

import java.util.List;

/** Resultado da emissão municipal, independente de provedor. */
public record ResultadoEmissao(
        StatusEmissaoMunicipal status, String numeroNfse, String codigoVerificacao, String protocolo,
        String xmlEnviado, String xmlRetorno, List<String> mensagens) {

    public ResultadoEmissao {
        mensagens = mensagens == null ? List.of() : List.copyOf(mensagens);
    }
}
