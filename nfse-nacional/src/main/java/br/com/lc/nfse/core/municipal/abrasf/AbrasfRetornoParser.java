package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Traduz o GerarNfseResposta (ABRASF) para o ResultadoEmissao canônico. */
public class AbrasfRetornoParser {

    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";

    public ResultadoEmissao parse(String xmlEnviado, String xmlResposta) {
        try {
            Document doc = parse(xmlResposta);
            NodeList msgs = doc.getElementsByTagNameNS(NS, "MensagemRetorno");
            if (msgs.getLength() > 0) {
                List<String> mensagens = new ArrayList<>();
                for (int i = 0; i < msgs.getLength(); i++) {
                    mensagens.add(texto(doc, "Codigo", i) + ": " + texto(doc, "Mensagem", i));
                }
                return new ResultadoEmissao(StatusEmissaoMunicipal.REJEITADA, null, null, null,
                        xmlEnviado, xmlResposta, mensagens);
            }
            String numero = primeiro(doc, "Numero");
            String codVerif = primeiro(doc, "CodigoVerificacao");
            return new ResultadoEmissao(StatusEmissaoMunicipal.AUTORIZADA, numero, codVerif, null,
                    xmlEnviado, xmlResposta, List.of());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao interpretar o retorno ABRASF", e);
        }
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // XXE hardening: disallow external entities and DOCTYPE declarations
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String primeiro(Document doc, String tag) {
        NodeList n = doc.getElementsByTagNameNS(NS, tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent();
    }

    private String texto(Document doc, String tag, int idx) {
        NodeList n = doc.getElementsByTagNameNS(NS, tag);
        Node item = idx < n.getLength() ? n.item(idx) : null;
        return item == null ? "" : item.getTextContent();
    }
}
