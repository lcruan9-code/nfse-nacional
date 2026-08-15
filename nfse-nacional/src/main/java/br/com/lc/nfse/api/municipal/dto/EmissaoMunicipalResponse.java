package br.com.lc.nfse.api.municipal.dto;

import br.com.lc.nfse.api.municipal.EmissaoMunicipal;

import java.util.List;
import java.util.UUID;

public record EmissaoMunicipalResponse(UUID id, String status, String numeroNfse, String codigoVerificacao,
                                       String protocolo, String xmlEnviado, List<String> mensagens) {

    public static EmissaoMunicipalResponse de(EmissaoMunicipal e) {
        return new EmissaoMunicipalResponse(e.getId(), e.getStatus().name(), e.getNumeroNfse(),
                e.getCodigoVerificacao(), e.getProtocolo(), e.getXmlEnviado(), e.mensagensLista());
    }
}
