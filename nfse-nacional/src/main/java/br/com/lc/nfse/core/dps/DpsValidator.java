package br.com.lc.nfse.core.dps;

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
 * Valida uma DPS (XML) contra o XSD oficial v1.01 da NFS-e Nacional.
 *
 * <p><b>Saneamento de patterns:</b> os XSDs oficiais foram escritos com âncoras
 * {@code ^ ... $} nos {@code <xs:pattern>} (semântica de regex .NET, usada pela Receita).
 * O validador do Java (Xerces) segue a spec do XSD, onde {@code ^} e {@code $} são
 * caracteres <i>literais</i> — então esses patterns rejeitam valores válidos. Antes de
 * compilar, removemos a âncora inicial {@code ^} e a final {@code $} de cada pattern,
 * fazendo o Xerces validar como a Receita pretendia. Unidade pura — sem Spring.
 */
public class DpsValidator {

    private static final String BASE = "/schemas/";
    private static final String XSD_RAIZ = "DPS_v1.01.xsd";

    /** Conjunto v1.01 completo — copiado/saneado para garantir resolução de includes/imports. */
    private static final List<String> ARQUIVOS_XSD = List.of(
            "CNC_v1.00.xsd", "DPS_v1.01.xsd", "NFSe_v1.01.xsd", "evento_v1.01.xsd",
            "pedRegEvento_v1.01.xsd", "tiposCnc_v1.00.xsd", "tiposComplexos_v1.01.xsd",
            "tiposEventos_v1.01.xsd", "tiposSimples_v1.01.xsd", "xmldsig-core-schema.xsd"
    );

    private static final Pattern XSD_PATTERN_FACET =
            Pattern.compile("(<xs:pattern\\s+value=\")([^\"]*)(\")");

    private final Schema schema;

    public DpsValidator() {
        this.schema = carregarSchema();
    }

    public ResultadoValidacao validar(String xml) {
        try {
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xml)));
            return ResultadoValidacao.ok();
        } catch (SAXException e) {
            return ResultadoValidacao.erro(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("Erro de I/O ao validar DPS", e);
        }
    }

    private Schema carregarSchema() {
        try {
            Path dir = Files.createTempDirectory("nfse-xsd-");
            dir.toFile().deleteOnExit();
            for (String nome : ARQUIVOS_XSD) {
                String saneado = sanearAncoras(lerRecurso(BASE + nome));
                Path destino = dir.resolve(nome);
                Files.writeString(destino, saneado, StandardCharsets.UTF_8);
                destino.toFile().deleteOnExit();
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return factory.newSchema(dir.resolve(XSD_RAIZ).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao preparar os XSDs saneados", e);
        } catch (SAXException e) {
            throw new IllegalStateException("Falha ao compilar o schema da DPS v1.01", e);
        }
    }

    /** Remove a âncora inicial {@code ^} e a final {@code $} de cada {@code <xs:pattern>}. */
    private String sanearAncoras(String xsd) {
        Matcher m = XSD_PATTERN_FACET.matcher(xsd);
        StringBuilder sb = new StringBuilder(xsd.length());
        while (m.find()) {
            String valor = m.group(2);
            if (valor.startsWith("^")) {
                valor = valor.substring(1);
            }
            if (valor.endsWith("$")) {
                valor = valor.substring(0, valor.length() - 1);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + valor + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String lerRecurso(String caminho) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(caminho)) {
            if (is == null) {
                throw new IllegalStateException("XSD não encontrado no classpath: " + caminho);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
