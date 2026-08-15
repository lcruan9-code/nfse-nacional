package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.EstiloEnvelope;

/** Monta o envelope SOAP da operação GerarNfse. Estilo por config (nesta fatia, nfseDadosMsg). */
public class AbrasfSoapEnvelope {

    public String envelopar(String xmlAssinado, EstiloEnvelope estilo) {
        return switch (estilo) {
            case NFSE_DADOS_MSG -> """
                <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">\
                <soap:Body><GerarNfse xmlns="http://www.abrasf.org.br/nfse.xsd">\
                <nfseDadosMsg>%s</nfseDadosMsg></GerarNfse></soap:Body></soap:Envelope>"""
                .formatted(xmlAssinado);
        };
    }
}
