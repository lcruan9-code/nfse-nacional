package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.xsd.ResultadoValidacao;
import br.com.lc.nfse.core.xsd.SanitizadorAncoras;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida um XML ABRASF (GerarNfseEnvio etc.) contra o XSD oficial 2.04, saneando âncoras ^$.
 *
 * <p><b>Defeito adicional do XSD oficial:</b> o elemento global {@code CompNfse} (linha ~1355 de
 * {@code nfse_v2.04.xsd}) carrega {@code minOccurs}/{@code maxOccurs} — atributos válidos apenas em
 * partículas dentro de um content model, não em declarações globais (XSD 1.0 §3.3.3). O Xerces rejeita
 * isso com {@code s4s-att-not-allowed}, o que impede a compilação do schema inteiro (não é um erro de
 * um XML específico). Removemos esses dois atributos exclusivamente da declaração global do
 * {@code CompNfse} antes de compilar — mesma categoria de saneamento do {@link SanitizadorAncoras},
 * porém específica deste XSD (não afeta o kernel ADN).
 */
public class AbrasfValidator {

    private static final String BASE = "/schemas/abrasf/";
    private static final String XSD_RAIZ = "nfse_v2.04.xsd";
    private static final List<String> ARQUIVOS = List.of(
            "nfse_v2.04.xsd", "xmldsig-core-schema20020212.xsd");

    private static final Pattern COMPNFSE_GLOBAL_INVALIDO = Pattern.compile(
            "(<xsd:element\\s+name=\"CompNfse\"\\s+type=\"tcCompNfse\")\\s+"
                    + "minOccurs=\"[^\"]*\"\\s+maxOccurs=\"[^\"]*\"\\s*(/>)");

    /** Schema compilado 1x por JVM e reaproveitado (evita recompilar o XSD a cada request). */
    private static volatile Schema schemaCache;

    private final Schema schema;

    public AbrasfValidator() { this.schema = obterSchema(); }

    private static Schema obterSchema() {
        Schema s = schemaCache;
        if (s == null) {
            synchronized (AbrasfValidator.class) {
                s = schemaCache;
                if (s == null) {
                    s = carregar();
                    schemaCache = s;
                }
            }
        }
        return s;
    }

    public ResultadoValidacao validar(String xml) {
        try {
            Validator v = schema.newValidator();
            v.validate(new StreamSource(new StringReader(xml)));
            return ResultadoValidacao.ok();
        } catch (SAXException e) {
            return ResultadoValidacao.erro(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("Erro de I/O ao validar XML ABRASF", e);
        }
    }

    private static Schema carregar() {
        try {
            Path dir = Files.createTempDirectory("abrasf-xsd-");
            dir.toFile().deleteOnExit();
            for (String nome : ARQUIVOS) {
                String saneado = corrigirElementoGlobalInvalido(SanitizadorAncoras.sanear(ler(BASE + nome)));
                Path destino = dir.resolve(nome);
                Files.writeString(destino, saneado, StandardCharsets.UTF_8);
                destino.toFile().deleteOnExit();
            }
            SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return f.newSchema(dir.resolve(XSD_RAIZ).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao preparar o XSD ABRASF", e);
        } catch (SAXException e) {
            throw new IllegalStateException("Falha ao compilar o schema ABRASF 2.04", e);
        }
    }

    /** Remove minOccurs/maxOccurs da declaração global inválida do CompNfse (ver javadoc da classe). */
    private static String corrigirElementoGlobalInvalido(String xsd) {
        Matcher m = COMPNFSE_GLOBAL_INVALIDO.matcher(xsd);
        return m.find() ? m.replaceAll("$1$2") : xsd;
    }

    private static String ler(String caminho) throws IOException {
        try (InputStream is = AbrasfValidator.class.getResourceAsStream(caminho)) {
            if (is == null) throw new IllegalStateException("XSD não encontrado: " + caminho);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
