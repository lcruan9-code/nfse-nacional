package br.com.lc.nfse;

import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke da Fase 1: prova que o conjunto oficial de XSDs v1.01 da DPS carrega e
 * resolve toda a cadeia de includes/imports (tiposComplexos → tiposSimples,
 * import do xmldsig-core-schema). Não valida um documento — só a consistência do schema.
 */
class SchemaSetTest {

    @Test
    void schemaDpsV101CarregaResolvendoIncludesEImports() throws Exception {
        URL url = getClass().getResource("/schemas/DPS_v1.01.xsd");
        assertNotNull(url, "DPS_v1.01.xsd deve estar no classpath em /schemas");

        // newSchema(File) resolve includes/imports relativos ao diretório do arquivo.
        File xsdRaiz = new File(url.toURI());
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        assertDoesNotThrow(() -> {
            Schema schema = factory.newSchema(xsdRaiz);
            assertNotNull(schema);
        });
    }
}
