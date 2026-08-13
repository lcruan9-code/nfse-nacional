package br.com.lc.nfse.core.dps;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DpsSignerTest {

    private final DpsBuilder builder = new DpsBuilder();
    private final DpsSigner signer = new DpsSigner();
    private final DpsValidator validator = new DpsValidator();

    @Test
    void dpsAssinadaTemAssinaturaQueVerifica() throws Exception {
        String xml = builder.construir(DpsBuilderTest.fixture());

        String assinado = signer.assinar(xml, certificadoDeTeste());

        assertTrue(assinado.contains("Signature"), "deve conter o elemento Signature");
        assertTrue(verificarAssinatura(assinado), "a assinatura deve verificar");
    }

    @Test
    void dpsAssinadaContinuaValidaNoXsd() {
        String assinado = signer.assinar(builder.construir(DpsBuilderTest.fixture()), certificadoDeTeste());

        ResultadoValidacao r = validator.validar(assinado);

        assertTrue(r.valido(), "DPS assinada deveria validar contra o XSD; erro: " + r.mensagem());
    }

    private CertificadoLoader.Certificado certificadoDeTeste() {
        InputStream p12 = getClass().getResourceAsStream("/certs/teste.p12");
        return new CertificadoLoader().carregar(p12, "changeit".toCharArray());
    }

    private boolean verificarAssinatura(String xmlAssinado) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlAssinado.getBytes(StandardCharsets.UTF_8)));

        Element infDps = (Element) doc.getElementsByTagNameNS(
                "http://www.sped.fazenda.gov.br/nfse", "infDPS").item(0);
        infDps.setIdAttributeNS(null, "Id", true);

        NodeList assinaturas = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertTrue(assinaturas.getLength() > 0, "sem elemento Signature no XML");

        DOMValidateContext ctx = new DOMValidateContext(
                certificadoDeTeste().certificate().getPublicKey(), assinaturas.item(0));
        XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
        return signature.validate(ctx);
    }
}
