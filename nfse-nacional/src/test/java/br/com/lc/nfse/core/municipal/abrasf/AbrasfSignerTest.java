package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.AlgoritmoAssinatura;
import br.com.lc.nfse.core.municipal.RpsRequest;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfSignerTest {

    private static final String DSIG = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";

    private RpsRequest rps() {
        return new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"), LocalDate.parse("2026-08-14"),
                "1500.00", 2, "01.01", "Consultoria", "4204608", 1, "11222333000181", "123", 2, 2);
    }

    @Test
    void assinaEVerifica_sha1() throws Exception {
        CertificadoLoader.Certificado cert = new CertificadoLoader().carregar(
                getClass().getResourceAsStream("/certs/teste.p12"), "changeit".toCharArray());
        String xml = new AbrasfXmlBuilder().montar(rps(), "2.04", "rps1");

        String assinado = new AbrasfSigner().assinar(xml, cert, AlgoritmoAssinatura.SHA1);
        assertThat(assinado).contains("<Signature").contains("rsa-sha1");

        Document doc = parse(assinado);
        Element inf = (Element) doc.getElementsByTagNameNS(NS, "InfDeclaracaoPrestacaoServico").item(0);
        inf.setIdAttributeNS(null, "Id", true);
        NodeList sigs = doc.getElementsByTagNameNS(DSIG, "Signature");
        DOMValidateContext ctx = new DOMValidateContext(cert.certificate().getPublicKey(), sigs.item(0));
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        XMLSignature sig = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
        assertThat(sig.validate(ctx)).isTrue();
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
