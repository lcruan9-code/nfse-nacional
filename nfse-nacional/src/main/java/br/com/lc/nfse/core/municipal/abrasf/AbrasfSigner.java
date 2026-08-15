package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.dps.AssinaturaException;
import br.com.lc.nfse.core.municipal.AlgoritmoAssinatura;
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
 * Assina o RPS ABRASF com XMLDSig enveloped. A Reference aponta o InfDeclaracaoPrestacaoServico
 * (atributo Id); a Signature entra como irmã dele, dentro do wrapper Rps (tcDeclaracaoPrestacaoServico).
 * ABRASF clássico = RSA-SHA1 + digest SHA1 + C14N inclusiva. Para SHA1 desligamos o secureValidation.
 */
public class AbrasfSigner {

    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";

    public String assinar(String xml, CertificadoLoader.Certificado cert, AlgoritmoAssinatura algoritmo) {
        try {
            Document doc = parse(xml);
            Element inf = (Element) doc.getElementsByTagNameNS(NS, "InfDeclaracaoPrestacaoServico").item(0);
            if (inf == null) throw new IllegalArgumentException("InfDeclaracaoPrestacaoServico não encontrado");
            String id = inf.getAttribute("Id");
            inf.setIdAttributeNS(null, "Id", true);
            Element wrapperRps = (Element) inf.getParentNode();

            String sigMethod = algoritmo == AlgoritmoAssinatura.SHA256
                    ? SignatureMethod.RSA_SHA256 : SignatureMethod.RSA_SHA1;
            String digMethod = algoritmo == AlgoritmoAssinatura.SHA256
                    ? DigestMethod.SHA256 : DigestMethod.SHA1;

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference("#" + id,
                    fac.newDigestMethod(digMethod, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null)),
                    null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(sigMethod, null), List.of(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509 = kif.newX509Data(List.of(cert.certificate()));
            KeyInfo ki = kif.newKeyInfo(List.of(x509));

            DOMSignContext ctx = new DOMSignContext(cert.privateKey(), wrapperRps);
            if (algoritmo == AlgoritmoAssinatura.SHA1) {
                ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
            }
            fac.newXMLSignature(si, ki).sign(ctx);
            return serialize(doc);
        } catch (RuntimeException e) {
            throw e instanceof AssinaturaException ae ? ae : new AssinaturaException("Falha ao assinar RPS ABRASF", e);
        } catch (Exception e) {
            throw new AssinaturaException("Falha ao assinar RPS ABRASF", e);
        }
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String serialize(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
