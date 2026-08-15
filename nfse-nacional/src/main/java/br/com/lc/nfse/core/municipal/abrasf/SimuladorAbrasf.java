package br.com.lc.nfse.core.municipal.abrasf;

import java.security.SecureRandom;

/** MODO LAB: devolve um GerarNfseResposta fictício (não transmite a nenhuma prefeitura). */
public class SimuladorAbrasf {

    private final SecureRandom random = new SecureRandom();

    public String responder(String simular, String numeroNfse, String xmlEnviado) {
        String pedido = (simular == null || simular.isBlank()) ? "AUTORIZADA" : simular.trim().toUpperCase();
        return switch (pedido) {
            case "AUTORIZADA" -> """
                <GerarNfseResposta xmlns="http://www.abrasf.org.br/nfse.xsd"><ListaNfse><CompNfse><Nfse>\
                <InfNfse><Numero>%s</Numero><CodigoVerificacao>%s</CodigoVerificacao></InfNfse>\
                </Nfse></CompNfse></ListaNfse></GerarNfseResposta>"""
                .formatted(numeroNfse, codVerif());
            case "REJEITADA" -> """
                <GerarNfseResposta xmlns="http://www.abrasf.org.br/nfse.xsd"><ListaMensagemRetorno>\
                <MensagemRetorno><Codigo>E9999</Codigo>\
                <Mensagem>Rejeição simulada no ambiente de laboratório</Mensagem></MensagemRetorno>\
                </ListaMensagemRetorno></GerarNfseResposta>""";
            default -> throw new IllegalArgumentException(
                    "simular inválido: " + simular + " (use AUTORIZADA ou REJEITADA)");
        };
    }

    private String codVerif() {
        StringBuilder sb = new StringBuilder(8);
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < 8; i++) sb.append(alfabeto.charAt(random.nextInt(alfabeto.length())));
        return sb.toString();
    }
}
