package br.com.lc.nfse.core.dps;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Assina a DPS com XMLDSig <i>enveloped</i>. A {@code Reference} aponta para o
 * {@code infDPS} (via atributo {@code Id}); a {@code Signature} entra como irmã de
 * {@code infDPS}, dentro de {@code DPS}, respeitando a sequência do XSD.
 *
 * <p>Algoritmos: RSA-SHA256 + digest SHA256 + C14N inclusiva + transform enveloped. (SHA-256
 * porque o Java 17 proíbe SHA-1 com <i>secure validation</i>, e é o padrão moderno.) A
 * conformidade exata com o ADN só importa na transmissão (fora do protótipo); aqui o critério
 * é assinar→verificar. Unidade pura — sem Spring.
 */
public class DpsSigner {

    private static final String NS_NFSE = "http://www.sped.fazenda.gov.br/nfse";

    public String assinar(String xmlDps, CertificadoLoader.Certificado certificado) {
        try {
            Document doc = parse(xmlDps);

            Element infDps = (Element) doc.getElementsByTagNameNS(NS_NFSE, "infDPS").item(0);
            if (infDps == null) {
                throw new IllegalArgumentException("Elemento infDPS não encontrado na DPS");
            }
            String id = infDps.getAttribute("Id");
            infDps.setIdAttributeNS(null, "Id", true); // habilita resolução da Reference "#id"

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            Reference reference = fac.newReference(
                    "#" + id,
                    fac.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(
                            fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null)
                    ),
                    null, null);

            SignedInfo signedInfo = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    List.of(reference));

            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509Data = kif.newX509Data(List.of(certificado.certificate()));
            KeyInfo keyInfo = kif.newKeyInfo(List.of(x509Data));

            Element dps = doc.getDocumentElement();
            DOMSignContext contexto = new DOMSignContext(certificado.privateKey(), dps);
            XMLSignature signature = fac.newXMLSignature(signedInfo, keyInfo);
            signature.sign(contexto);

            return serialize(doc);
        } catch (RuntimeException e) {
            throw e instanceof AssinaturaException ae ? ae : new AssinaturaException("Falha ao assinar a DPS", e);
        } catch (Exception e) {
            throw new AssinaturaException("Falha ao assinar a DPS", e);
        }
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String serialize(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
