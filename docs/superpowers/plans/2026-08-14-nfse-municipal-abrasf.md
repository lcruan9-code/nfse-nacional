# NFS-e Municipal (ABRASF 2.x) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emitir NFS-e no padrão municipal ABRASF 2.04 (cidades fora do ADN) por uma rota separada `POST /v1/nfse-municipal`, em modo laboratório (monta → valida no XSD oficial → assina → envelope SOAP → simula a resposta), com roteamento por código IBGE.

**Architecture:** Pacote novo isolado `core/municipal/*` (motor offline puro) + `api/municipal/*` (web/persistência), espelhando o split do ADN (`core/dps` + `api/emissao`). Strategy (`ProvedorMunicipal`) + Factory (`FabricaProvedor`); `ResolvedorProvedor` roteia por IBGE a partir da tabela `provedores_municipais`. Nada do caminho ADN é alterado, exceto a extração de um kernel XSD compartilhado (`core/xsd`).

**Tech Stack:** Java 17, Spring Boot 4.1, JPA + Flyway (PostgreSQL 16), XMLDSig (javax.xml.crypto), Testcontainers 1.21.4, JUnit 5 + AssertJ.

## Global Constraints

- Build/roda no **JDK 17**. NUNCA alterar `JAVA_HOME` global (Java 8 dos migradores). Compilar com `./mvnw` (aponta o toolchain 17 por sessão).
- **Não alterar** o caminho ADN (`core/dps` exceto extração para `core/xsd`; `api/emissao`; `web/*`).
- Namespace ABRASF: `http://www.abrasf.org.br/nfse.xsd`; `elementFormDefault="qualified"` (todo elemento no namespace).
- Assinatura ABRASF clássico: **RSA-SHA1 + digest SHA1 + C14N inclusiva + transform enveloped**; `secureValidation=false` apenas no caminho SHA1. Algoritmo é campo de `ProvedorConfig` (default SHA1).
- Multi-tenant: toda leitura/escrita escopada por `contaId` (padrão `findByIdAndContaId` → 404 cross-tenant).
- Chave de produção (`sk_live_`, `Ambiente.PRODUCAO`) → **501** (sem transmissão real nesta fatia).
- Sem segredos no repo. Commits pequenos e frequentes, um por task (no mínimo).
- Modo laboratório: o critério de "pronto" de cada componente do motor é o **teste JUnit**; nada é transmitido a prefeitura real.

---

## File Structure

**Criados:**
- `core/xsd/SanitizadorAncoras.java` — remove âncoras `^$` de `<xs:pattern>`/`<xsd:pattern>` (compartilhado ADN+ABRASF).
- `core/xsd/ResultadoValidacao.java` — movido de `core/dps/` (value type genérico).
- `core/municipal/TipoProvedor.java`, `AlgoritmoAssinatura.java`, `EstiloEnvelope.java`, `StatusEmissaoMunicipal.java` — enums.
- `core/municipal/RpsRequest.java`, `ProvedorConfig.java`, `ResultadoEmissao.java` — records (modelo canônico).
- `core/municipal/ProvedorMunicipal.java` — interface (Strategy).
- `core/municipal/FabricaProvedor.java` — Factory.
- `core/municipal/registro/ResolvedorProvedor.java`, `ResolucaoProvedor.java`, `ProvedorMunicipalRegistro.java` (entity), `ProvedorMunicipalRegistroRepository.java`.
- `core/municipal/abrasf/AbrasfValidator.java`, `AbrasfXmlBuilder.java`, `AbrasfSigner.java`, `AbrasfSoapEnvelope.java`, `SimuladorAbrasf.java`, `AbrasfRetornoParser.java`, `AbrasfProvedor.java`.
- `api/municipal/MunicipalController.java`, `EmissaoMunicipalService.java`, `EmissaoMunicipal.java` (entity), `EmissaoMunicipalRepository.java`, `dto/EmitirNfseMunicipalRequest.java`, `dto/EmissaoMunicipalResponse.java`.
- `resources/schemas/abrasf/nfse_v2.04.xsd`, `xmldsig-core-schema20020212.xsd` — **já copiados** para o projeto (confirmar presença na Task 1).
- `resources/db/migration/V5__provedores_municipais.sql`, `V6__emissoes_municipais.sql`.
- Testes espelhando cada componente em `src/test/java/.../`.

**Modificados (mínimo, justificado):**
- `core/dps/DpsValidator.java` — passa a usar `core/xsd/SanitizadorAncoras` e `core/xsd/ResultadoValidacao`.
- `api/emissao/EmissaoService.java` e `core/dps/DpsValidatorTest.java` — atualizar import de `ResultadoValidacao`.
- `src/test/java/.../api/AbstractPostgresIT.java` — incluir as novas tabelas no `truncate`.

---

## Task 1: Kernel XSD compartilhado + validador ABRASF

**Files:**
- Create: `src/main/java/br/com/lc/nfse/core/xsd/SanitizadorAncoras.java`
- Create: `src/main/java/br/com/lc/nfse/core/xsd/ResultadoValidacao.java`
- Delete: `src/main/java/br/com/lc/nfse/core/dps/ResultadoValidacao.java`
- Modify: `src/main/java/br/com/lc/nfse/core/dps/DpsValidator.java`
- Modify: `src/main/java/br/com/lc/nfse/api/emissao/EmissaoService.java` (import)
- Modify: `src/test/java/br/com/lc/nfse/core/dps/DpsValidatorTest.java` (import)
- Create: `src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfValidator.java`
- Verify present: `src/main/resources/schemas/abrasf/nfse_v2.04.xsd`, `.../xmldsig-core-schema20020212.xsd`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfValidatorTest.java`

**Interfaces:**
- Produces: `SanitizadorAncoras.sanear(String xsd) -> String` (static);
  `core.xsd.ResultadoValidacao` (record `{boolean valido, String mensagem}` com `ok()`/`erro(String)`);
  `AbrasfValidator` com ctor sem args e `ResultadoValidacao validar(String xml)`.

- [ ] **Step 1: Confirmar os XSDs no classpath**

Run: `ls src/main/resources/schemas/abrasf/`
Expected: `nfse_v2.04.xsd` e `xmldsig-core-schema20020212.xsd` presentes. Se faltarem, o build da Task falha — recopiar do design (§7 do spec).

- [ ] **Step 2: Escrever o teste do validador ABRASF (falha)**

Create `src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfValidatorTest.java`:

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.xsd.ResultadoValidacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfValidatorTest {

    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";

    private String gerarNfseEnvioMinimo() {
        return """
            <GerarNfseEnvio xmlns="%s">
              <Rps>
                <InfDeclaracaoPrestacaoServico Id="rps1">
                  <Rps>
                    <IdentificacaoRps><Numero>1</Numero><Serie>1</Serie><Tipo>1</Tipo></IdentificacaoRps>
                    <DataEmissao>2026-08-14</DataEmissao>
                    <Status>1</Status>
                  </Rps>
                  <Competencia>2026-08-14</Competencia>
                  <Servico>
                    <Valores><ValorServicos>1500.00</ValorServicos></Valores>
                    <IssRetido>2</IssRetido>
                    <ItemListaServico>01.01</ItemListaServico>
                    <Discriminacao>Consultoria em TI</Discriminacao>
                    <CodigoMunicipio>4204608</CodigoMunicipio>
                    <ExigibilidadeISS>1</ExigibilidadeISS>
                  </Servico>
                  <Prestador>
                    <CpfCnpj><Cnpj>11222333000181</Cnpj></CpfCnpj>
                    <InscricaoMunicipal>123</InscricaoMunicipal>
                  </Prestador>
                  <OptanteSimplesNacional>2</OptanteSimplesNacional>
                  <IncentivoFiscal>2</IncentivoFiscal>
                </InfDeclaracaoPrestacaoServico>
              </Rps>
            </GerarNfseEnvio>
            """.formatted(NS);
    }

    @Test
    void xmlMinimoValido_passa() {
        ResultadoValidacao r = new AbrasfValidator().validar(gerarNfseEnvioMinimo());
        assertThat(r.valido()).as(r.mensagem()).isTrue();
    }

    @Test
    void xmlSemValorServicos_falha() {
        String xml = gerarNfseEnvioMinimo().replace(
            "<Valores><ValorServicos>1500.00</ValorServicos></Valores>", "<Valores></Valores>");
        assertThat(new AbrasfValidator().validar(xml).valido()).isFalse();
    }
}
```

- [ ] **Step 3: Rodar — falha por não compilar**

Run: `./mvnw -q test -Dtest=AbrasfValidatorTest`
Expected: FAIL de compilação (`AbrasfValidator` e `core.xsd.ResultadoValidacao` não existem).

- [ ] **Step 4: Criar `core/xsd/ResultadoValidacao.java`**

```java
package br.com.lc.nfse.core.xsd;

/** Resultado da validação de um XML contra um XSD. Value type genérico (ADN + municipal). */
public record ResultadoValidacao(boolean valido, String mensagem) {
    public static ResultadoValidacao ok() { return new ResultadoValidacao(true, null); }
    public static ResultadoValidacao erro(String mensagem) { return new ResultadoValidacao(false, mensagem); }
}
```

- [ ] **Step 5: Criar `core/xsd/SanitizadorAncoras.java`** (extraído do DpsValidator; regex aceita `xs:` e `xsd:`)

```java
package br.com.lc.nfse.core.xsd;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remove a âncora inicial {@code ^} e a final {@code $} de cada {@code <xs:pattern>}/{@code <xsd:pattern>}.
 * Os XSDs fiscais BR foram escritos com semântica de regex .NET (^...$); o Xerces trata ^ e $ como
 * literais e rejeita valores válidos. Saneamos antes de compilar o schema.
 */
public final class SanitizadorAncoras {

    private static final Pattern PATTERN_FACET =
            Pattern.compile("(<xs[d]?:pattern\\s+value=\")([^\"]*)(\")");

    private SanitizadorAncoras() {}

    public static String sanear(String xsd) {
        Matcher m = PATTERN_FACET.matcher(xsd);
        StringBuilder sb = new StringBuilder(xsd.length());
        while (m.find()) {
            String valor = m.group(2);
            if (valor.startsWith("^")) valor = valor.substring(1);
            if (valor.endsWith("$")) valor = valor.substring(0, valor.length() - 1);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + valor + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
```

- [ ] **Step 6: Refatorar `DpsValidator.java`** — apagar o método `sanearAncoras` e o campo `XSD_PATTERN_FACET`; trocar a chamada e os imports.

Em `carregarSchema()`, trocar `sanearAncoras(lerRecurso(...))` por `SanitizadorAncoras.sanear(lerRecurso(...))`.
Remover imports `java.util.regex.Matcher`/`Pattern` e o campo/método citados.
Adicionar imports:
```java
import br.com.lc.nfse.core.xsd.ResultadoValidacao;
import br.com.lc.nfse.core.xsd.SanitizadorAncoras;
```
Apagar `src/main/java/br/com/lc/nfse/core/dps/ResultadoValidacao.java`.

- [ ] **Step 7: Ajustar imports em TODOS os arquivos que referenciam `ResultadoValidacao`**

`ResultadoValidacao` é usado em 6 arquivos além do próprio. Após mover para `core/xsd`:
- **Trocar** o import existente `br.com.lc.nfse.core.dps.ResultadoValidacao` → `br.com.lc.nfse.core.xsd.ResultadoValidacao` em: `api/emissao/EmissaoService.java`, `web/DpsPreviewController.java`.
- **Adicionar** `import br.com.lc.nfse.core.xsd.ResultadoValidacao;` (hoje usam sem import, por estarem no mesmo pacote `core.dps`) em: `core/dps/DpsValidator.java` (se ainda não adicionado no Step 6), `src/test/java/.../core/dps/DpsBuilderTest.java`, `src/test/java/.../core/dps/DpsSignerTest.java`, `src/test/java/.../core/dps/DpsValidatorTest.java`.
- Conferir com `grep -rln ResultadoValidacao src/` que nenhum arquivo ficou sem o import novo, e compilar (`./mvnw -q -DskipTests test-compile`) para garantir.

- [ ] **Step 8: Criar `AbrasfValidator.java`**

```java
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

/** Valida um XML ABRASF (GerarNfseEnvio etc.) contra o XSD oficial 2.04, saneando âncoras ^$. */
public class AbrasfValidator {

    private static final String BASE = "/schemas/abrasf/";
    private static final String XSD_RAIZ = "nfse_v2.04.xsd";
    private static final List<String> ARQUIVOS = List.of(
            "nfse_v2.04.xsd", "xmldsig-core-schema20020212.xsd");

    private final Schema schema;

    public AbrasfValidator() { this.schema = carregar(); }

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

    private Schema carregar() {
        try {
            Path dir = Files.createTempDirectory("abrasf-xsd-");
            dir.toFile().deleteOnExit();
            for (String nome : ARQUIVOS) {
                String saneado = SanitizadorAncoras.sanear(ler(BASE + nome));
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

    private String ler(String caminho) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(caminho)) {
            if (is == null) throw new IllegalStateException("XSD não encontrado: " + caminho);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 9: Rodar os testes tocados — todos verdes**

Run: `./mvnw -q test -Dtest=AbrasfValidatorTest,DpsValidatorTest,DpsBuilderTest`
Expected: PASS (validador ABRASF verde; ADN não regrediu).

- [ ] **Step 10: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/xsd \
        nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfValidator.java \
        nfse-nacional/src/main/resources/schemas/abrasf \
        nfse-nacional/src/main/java/br/com/lc/nfse/core/dps/DpsValidator.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/api/emissao/EmissaoService.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/web/DpsPreviewController.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core
git rm nfse-nacional/src/main/java/br/com/lc/nfse/core/dps/ResultadoValidacao.java
git commit -m "feat: kernel XSD compartilhado (core/xsd) + validador ABRASF 2.04"
```

---

## Task 2: Modelo canônico + interface (SPI)

**Files:**
- Create: `core/municipal/TipoProvedor.java`, `AlgoritmoAssinatura.java`, `EstiloEnvelope.java`, `StatusEmissaoMunicipal.java`
- Create: `core/municipal/RpsRequest.java`, `ProvedorConfig.java`, `ResultadoEmissao.java`
- Create: `core/municipal/ProvedorMunicipal.java`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/ModeloCanonicoTest.java`

**Interfaces:**
- Produces:
  - `enum TipoProvedor { ADN, ABRASF_2X }`
  - `enum AlgoritmoAssinatura { SHA1, SHA256 }`
  - `enum EstiloEnvelope { NFSE_DADOS_MSG }`
  - `enum StatusEmissaoMunicipal { AUTORIZADA, REJEITADA }`
  - `record RpsRequest(String numero, String serie, String tipo, java.time.LocalDate dataEmissao, java.time.LocalDate competencia, String valorServicos, int issRetido, String itemListaServico, String discriminacao, String codigoMunicipioIbge, int exigibilidadeIss, String prestadorCnpj, String prestadorInscricaoMunicipal, int optanteSimplesNacional, int incentivoFiscal)`
  - `record ProvedorConfig(TipoProvedor tipo, String versaoAbrasf, String urlHomolog, String urlProd, EstiloEnvelope estiloEnvelope, AlgoritmoAssinatura algoritmo)`
  - `record ResultadoEmissao(StatusEmissaoMunicipal status, String numeroNfse, String codigoVerificacao, String protocolo, String xmlEnviado, String xmlRetorno, java.util.List<String> mensagens)`
  - `interface ProvedorMunicipal { TipoProvedor tipo(); ResultadoEmissao emitir(RpsRequest rps, br.com.lc.nfse.core.cert.CertificadoLoader.Certificado cert, ProvedorConfig cfg); }`

- [ ] **Step 1: Escrever o teste de sanidade (falha)**

Create `src/test/java/br/com/lc/nfse/core/municipal/ModeloCanonicoTest.java`:

```java
package br.com.lc.nfse.core.municipal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModeloCanonicoTest {

    @Test
    void constroiModeloCanonico() {
        ProvedorConfig cfg = new ProvedorConfig(TipoProvedor.ABRASF_2X, "2.04",
                "https://hom.exemplo/ws", "https://prod.exemplo/ws",
                EstiloEnvelope.NFSE_DADOS_MSG, AlgoritmoAssinatura.SHA1);
        RpsRequest rps = new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"),
                LocalDate.parse("2026-08-14"), "1500.00", 2, "01.01", "Consultoria",
                "4204608", 1, "11222333000181", "123", 2, 2);
        ResultadoEmissao r = new ResultadoEmissao(StatusEmissaoMunicipal.AUTORIZADA, "10", "ABC123",
                "P1", "<xml/>", "<resp/>", List.of());

        assertThat(cfg.algoritmo()).isEqualTo(AlgoritmoAssinatura.SHA1);
        assertThat(rps.codigoMunicipioIbge()).isEqualTo("4204608");
        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.AUTORIZADA);
    }
}
```

- [ ] **Step 2: Rodar — falha de compilação**

Run: `./mvnw -q test -Dtest=ModeloCanonicoTest`
Expected: FAIL (tipos inexistentes).

- [ ] **Step 3: Criar os quatro enums**

`core/municipal/TipoProvedor.java`:
```java
package br.com.lc.nfse.core.municipal;
/** Modo de emissão por município. ADN = Padrão Nacional; ABRASF_2X = legado municipal. */
public enum TipoProvedor { ADN, ABRASF_2X }
```
`core/municipal/AlgoritmoAssinatura.java`:
```java
package br.com.lc.nfse.core.municipal;
public enum AlgoritmoAssinatura { SHA1, SHA256 }
```
`core/municipal/EstiloEnvelope.java`:
```java
package br.com.lc.nfse.core.municipal;
/** Estilo do envelope SOAP. Nesta fatia só NFSE_DADOS_MSG; futuros provedores adicionam variações. */
public enum EstiloEnvelope { NFSE_DADOS_MSG }
```
`core/municipal/StatusEmissaoMunicipal.java`:
```java
package br.com.lc.nfse.core.municipal;
public enum StatusEmissaoMunicipal { AUTORIZADA, REJEITADA }
```

- [ ] **Step 4: Criar os três records**

`core/municipal/RpsRequest.java`:
```java
package br.com.lc.nfse.core.municipal;

import java.time.LocalDate;

/** Modelo canônico interno da nota — neutro de dialeto. Cada provedor traduz deste modelo. */
public record RpsRequest(
        String numero, String serie, String tipo,
        LocalDate dataEmissao, LocalDate competencia,
        String valorServicos, int issRetido, String itemListaServico, String discriminacao,
        String codigoMunicipioIbge, int exigibilidadeIss,
        String prestadorCnpj, String prestadorInscricaoMunicipal,
        int optanteSimplesNacional, int incentivoFiscal) {}
```
`core/municipal/ProvedorConfig.java`:
```java
package br.com.lc.nfse.core.municipal;

/** Config de emissão por provedor/município, resolvida a partir do IBGE. */
public record ProvedorConfig(
        TipoProvedor tipo, String versaoAbrasf,
        String urlHomolog, String urlProd,
        EstiloEnvelope estiloEnvelope, AlgoritmoAssinatura algoritmo) {}
```
`core/municipal/ResultadoEmissao.java`:
```java
package br.com.lc.nfse.core.municipal;

import java.util.List;

/** Resultado da emissão municipal, independente de provedor. */
public record ResultadoEmissao(
        StatusEmissaoMunicipal status, String numeroNfse, String codigoVerificacao, String protocolo,
        String xmlEnviado, String xmlRetorno, List<String> mensagens) {

    public ResultadoEmissao {
        mensagens = mensagens == null ? List.of() : List.copyOf(mensagens);
    }
}
```

- [ ] **Step 5: Criar a interface `ProvedorMunicipal.java`**

```java
package br.com.lc.nfse.core.municipal;

import br.com.lc.nfse.core.cert.CertificadoLoader;

/** Strategy: todo provedor municipal implementa este contrato único. */
public interface ProvedorMunicipal {
    TipoProvedor tipo();
    ResultadoEmissao emitir(RpsRequest rps, CertificadoLoader.Certificado cert, ProvedorConfig cfg);
}
```

- [ ] **Step 6: Rodar — verde**

Run: `./mvnw -q test -Dtest=ModeloCanonicoTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/*.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core/municipal/ModeloCanonicoTest.java
git commit -m "feat: modelo canônico e interface ProvedorMunicipal (SPI municipal)"
```

---

## Task 3: AbrasfXmlBuilder (2.04) — monta o GerarNfseEnvio

**Files:**
- Create: `core/municipal/abrasf/AbrasfXmlBuilder.java`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfXmlBuilderTest.java`

**Interfaces:**
- Consumes: `RpsRequest` (Task 2); `AbrasfValidator` (Task 1).
- Produces: `AbrasfXmlBuilder` com `String montar(RpsRequest rps, String versaoAbrasf, String idInfDeclaracao)`.
  Nesta fatia só `"2.04"` é suportado; outra versão lança `IllegalArgumentException`.

- [ ] **Step 1: Escrever o teste-âncora (falha)** — o XML montado valida no XSD ABRASF

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.RpsRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbrasfXmlBuilderTest {

    private RpsRequest rps() {
        return new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"), LocalDate.parse("2026-08-14"),
                "1500.00", 2, "01.01", "Consultoria em TI", "4204608", 1,
                "11222333000181", "123", 2, 2);
    }

    @Test
    void montaXmlQueValidaNoXsd() {
        String xml = new AbrasfXmlBuilder().montar(rps(), "2.04", "rps1");
        assertThat(xml).contains("<GerarNfseEnvio").contains("Id=\"rps1\"");
        assertThat(new AbrasfValidator().validar(xml).valido())
                .as("XML deve validar no XSD ABRASF 2.04").isTrue();
    }

    @Test
    void versaoNaoSuportada_lanca() {
        assertThatThrownBy(() -> new AbrasfXmlBuilder().montar(rps(), "1.00", "rps1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Rodar — falha**

Run: `./mvnw -q test -Dtest=AbrasfXmlBuilderTest`
Expected: FAIL (classe não existe).

- [ ] **Step 3: Implementar `AbrasfXmlBuilder.java`**

Ordem da sequência do XSD (`tcInfDeclaracaoPrestacaoServico`): Rps, Competencia, Servico, Prestador, [Tomador...], OptanteSimplesNacional, IncentivoFiscal. Campos obrigatórios do Servico: Valores(ValorServicos), IssRetido, ItemListaServico, Discriminacao, CodigoMunicipio, ExigibilidadeISS.

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.RpsRequest;

import java.time.format.DateTimeFormatter;

/** Monta o GerarNfseEnvio (ABRASF). Ramifica por versão; nesta fatia só a 2.04. */
public class AbrasfXmlBuilder {

    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String montar(RpsRequest r, String versaoAbrasf, String idInfDeclaracao) {
        if (!"2.04".equals(versaoAbrasf)) {
            throw new IllegalArgumentException("Versão ABRASF não suportada nesta fatia: " + versaoAbrasf);
        }
        String x = esc(r.discriminacao());
        return """
            <GerarNfseEnvio xmlns="%s"><Rps><InfDeclaracaoPrestacaoServico Id="%s">\
            <Rps><IdentificacaoRps><Numero>%s</Numero><Serie>%s</Serie><Tipo>%s</Tipo></IdentificacaoRps>\
            <DataEmissao>%s</DataEmissao><Status>1</Status></Rps>\
            <Competencia>%s</Competencia>\
            <Servico><Valores><ValorServicos>%s</ValorServicos></Valores>\
            <IssRetido>%d</IssRetido><ItemListaServico>%s</ItemListaServico>\
            <Discriminacao>%s</Discriminacao><CodigoMunicipio>%s</CodigoMunicipio>\
            <ExigibilidadeISS>%d</ExigibilidadeISS></Servico>\
            <Prestador><CpfCnpj><Cnpj>%s</Cnpj></CpfCnpj><InscricaoMunicipal>%s</InscricaoMunicipal></Prestador>\
            <OptanteSimplesNacional>%d</OptanteSimplesNacional><IncentivoFiscal>%d</IncentivoFiscal>\
            </InfDeclaracaoPrestacaoServico></Rps></GerarNfseEnvio>"""
            .formatted(NS, idInfDeclaracao,
                r.numero(), r.serie(), r.tipo(),
                r.dataEmissao().format(DATA), r.competencia().format(DATA),
                r.valorServicos(), r.issRetido(), r.itemListaServico(),
                x, r.codigoMunicipioIbge(), r.exigibilidadeIss(),
                r.prestadorCnpj(), r.prestadorInscricaoMunicipal(),
                r.optanteSimplesNacional(), r.incentivoFiscal());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 4: Rodar — verde**

Run: `./mvnw -q test -Dtest=AbrasfXmlBuilderTest`
Expected: PASS (ambos os testes). Se o XSD reprovar algum campo, ajustar a ordem/valores ao `nfse_v2.04.xsd` (o validador aponta o elemento).

- [ ] **Step 5: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfXmlBuilder.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfXmlBuilderTest.java
git commit -m "feat: AbrasfXmlBuilder 2.04 (GerarNfseEnvio valida no XSD oficial)"
```

---

## Task 4: AbrasfSigner — XMLDSig sobre o InfDeclaracaoPrestacaoServico

**Files:**
- Create: `core/municipal/abrasf/AbrasfSigner.java`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfSignerTest.java`

**Interfaces:**
- Consumes: `AlgoritmoAssinatura` (Task 2); `RpsRequest`/`AbrasfXmlBuilder` (Task 3); `CertificadoLoader.Certificado`.
- Produces: `AbrasfSigner` com `String assinar(String xml, CertificadoLoader.Certificado cert, AlgoritmoAssinatura algoritmo)`.
  A `Signature` entra como irmã de `InfDeclaracaoPrestacaoServico`, dentro do `<Rps>` wrapper.

- [ ] **Step 1: Escrever o teste (falha)** — assina e a assinatura verifica

```java
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
```

> **Certificado nos testes unitários:** usa-se o cert de teste `/certs/teste.p12` (senha `changeit`), o mesmo do `DpsSignerTest`, via `CertificadoLoader.carregar(InputStream, char[])`. O `sandbox.p12` (senha `sandbox`) é o de runtime, servido pelo `CertificadoProvider` — usado só no teste de integração (Task 11).

- [ ] **Step 2: Rodar — falha**

Run: `./mvnw -q test -Dtest=AbrasfSignerTest`
Expected: FAIL (classe não existe).

- [ ] **Step 3: Implementar `AbrasfSigner.java`**

```java
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
```

- [ ] **Step 4: Rodar — verde**

Run: `./mvnw -q test -Dtest=AbrasfSignerTest`
Expected: PASS. (Reusa `AssinaturaException` do `core/dps`.)

- [ ] **Step 5: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfSigner.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfSignerTest.java
git commit -m "feat: AbrasfSigner (XMLDSig RSA-SHA1/SHA256 por config, C14N inclusiva)"
```

---

## Task 5: AbrasfSoapEnvelope — envelope da operação GerarNfse

**Files:**
- Create: `core/municipal/abrasf/AbrasfSoapEnvelope.java`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfSoapEnvelopeTest.java`

**Interfaces:**
- Consumes: `EstiloEnvelope` (Task 2).
- Produces: `AbrasfSoapEnvelope` com `String envelopar(String xmlAssinado, EstiloEnvelope estilo)`.

- [ ] **Step 1: Escrever o teste (falha)**

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.EstiloEnvelope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfSoapEnvelopeTest {

    @Test
    void envelopaComNfseDadosMsg() {
        String env = new AbrasfSoapEnvelope().envelopar("<GerarNfseEnvio/>", EstiloEnvelope.NFSE_DADOS_MSG);
        assertThat(env).contains("soap:Envelope").contains("GerarNfse").contains("<GerarNfseEnvio/>");
    }
}
```

- [ ] **Step 2: Rodar — falha**

Run: `./mvnw -q test -Dtest=AbrasfSoapEnvelopeTest`
Expected: FAIL.

- [ ] **Step 3: Implementar `AbrasfSoapEnvelope.java`**

```java
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
```

- [ ] **Step 4: Rodar — verde**

Run: `./mvnw -q test -Dtest=AbrasfSoapEnvelopeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfSoapEnvelope.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfSoapEnvelopeTest.java
git commit -m "feat: AbrasfSoapEnvelope (operação GerarNfse, estilo por config)"
```

---

## Task 6: SimuladorAbrasf + AbrasfRetornoParser (modo laboratório)

**Files:**
- Create: `core/municipal/abrasf/SimuladorAbrasf.java`
- Create: `core/municipal/abrasf/AbrasfRetornoParser.java`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/abrasf/SimuladorAbrasfTest.java`

**Interfaces:**
- Consumes: `StatusEmissaoMunicipal`, `ResultadoEmissao` (Task 2).
- Produces:
  - `SimuladorAbrasf` com `String responder(String simular, String numeroNfse, String xmlEnviado)` → devolve um `GerarNfseResposta` fictício (feliz ou rejeição).
  - `AbrasfRetornoParser` com `ResultadoEmissao parse(String xmlEnviado, String xmlResposta)`.

- [ ] **Step 1: Escrever o teste (falha)**

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimuladorAbrasfTest {

    @Test
    void autorizada_temNumeroECodigo() {
        String resp = new SimuladorAbrasf().responder("AUTORIZADA", "10", "<GerarNfseEnvio/>");
        ResultadoEmissao r = new AbrasfRetornoParser().parse("<GerarNfseEnvio/>", resp);
        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.AUTORIZADA);
        assertThat(r.numeroNfse()).isEqualTo("10");
        assertThat(r.codigoVerificacao()).isNotBlank();
        assertThat(r.xmlRetorno()).contains("CompNfse");
    }

    @Test
    void rejeitada_temMensagem() {
        String resp = new SimuladorAbrasf().responder("REJEITADA", "10", "<GerarNfseEnvio/>");
        ResultadoEmissao r = new AbrasfRetornoParser().parse("<GerarNfseEnvio/>", resp);
        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.REJEITADA);
        assertThat(r.mensagens()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Rodar — falha**

Run: `./mvnw -q test -Dtest=SimuladorAbrasfTest`
Expected: FAIL.

- [ ] **Step 3: Implementar `SimuladorAbrasf.java`**

```java
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
```

- [ ] **Step 4: Implementar `AbrasfRetornoParser.java`**

```java
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
```

- [ ] **Step 5: Rodar — verde**

Run: `./mvnw -q test -Dtest=SimuladorAbrasfTest`
Expected: PASS (ambos os testes).

- [ ] **Step 6: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/SimuladorAbrasf.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfRetornoParser.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core/municipal/abrasf/SimuladorAbrasfTest.java
git commit -m "feat: SimuladorAbrasf + AbrasfRetornoParser (retorno lab -> ResultadoEmissao)"
```

---

## Task 7: AbrasfProvedor + FabricaProvedor (orquestra o pipeline)

**Files:**
- Create: `core/municipal/abrasf/AbrasfProvedor.java`
- Create: `core/municipal/FabricaProvedor.java`
- Test: `src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfProvedorTest.java`

**Interfaces:**
- Consumes: tudo das Tasks 1-6.
- Produces:
  - `AbrasfProvedor implements ProvedorMunicipal` (ctor sem args; instancia os auxiliares internamente).
    A simulação usa um campo `simular` — como `RpsRequest` não o carrega, o `AbrasfProvedor` lê de um
    `ThreadLocal`? **Não.** Em vez disso, `emitir` deriva o `simular` de forma fixa "AUTORIZADA" e o
    controle de rejeição fica na camada de serviço (Task 10) via um método sobrecarregado. Para manter o
    contrato `ProvedorMunicipal.emitir` limpo, `AbrasfProvedor` expõe também
    `ResultadoEmissao emitir(RpsRequest, Certificado, ProvedorConfig, String simular)` e a versão da
    interface chama essa com `"AUTORIZADA"`.
  - `FabricaProvedor` com `ProvedorMunicipal criar(TipoProvedor tipo)`.

- [ ] **Step 1: Escrever o teste (falha)**

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.AlgoritmoAssinatura;
import br.com.lc.nfse.core.municipal.EstiloEnvelope;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.RpsRequest;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import br.com.lc.nfse.core.municipal.TipoProvedor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfProvedorTest {

    private ProvedorConfig cfg() {
        return new ProvedorConfig(TipoProvedor.ABRASF_2X, "2.04", "https://hom/ws", "https://prod/ws",
                EstiloEnvelope.NFSE_DADOS_MSG, AlgoritmoAssinatura.SHA1);
    }

    private RpsRequest rps() {
        return new RpsRequest("1", "1", "1", LocalDate.parse("2026-08-14"), LocalDate.parse("2026-08-14"),
                "1500.00", 2, "01.01", "Consultoria", "4204608", 1, "11222333000181", "123", 2, 2);
    }

    @Test
    void emiteAutorizadaComXmlAssinado() throws Exception {
        CertificadoLoader.Certificado cert = new CertificadoLoader().carregar(
                getClass().getResourceAsStream("/certs/teste.p12"), "changeit".toCharArray());
        ResultadoEmissao r = new AbrasfProvedor().emitir(rps(), cert, cfg(), "AUTORIZADA");

        assertThat(r.status()).isEqualTo(StatusEmissaoMunicipal.AUTORIZADA);
        assertThat(r.numeroNfse()).isEqualTo("1");
        assertThat(r.xmlEnviado()).contains("<Signature").contains("<GerarNfseEnvio");
    }

    @Test
    void tipoEhAbrasf() {
        assertThat(new AbrasfProvedor().tipo()).isEqualTo(TipoProvedor.ABRASF_2X);
    }
}
```

- [ ] **Step 2: Rodar — falha**

Run: `./mvnw -q test -Dtest=AbrasfProvedorTest`
Expected: FAIL.

- [ ] **Step 3: Implementar `AbrasfProvedor.java`**

```java
package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.ProvedorMunicipal;
import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.RpsRequest;
import br.com.lc.nfse.core.municipal.TipoProvedor;

/** Adaptador ABRASF 2.x: monta -> valida -> assina -> envelopa -> [lab] simula -> parseia. */
public class AbrasfProvedor implements ProvedorMunicipal {

    private final AbrasfXmlBuilder builder = new AbrasfXmlBuilder();
    private final AbrasfValidator validator = new AbrasfValidator();
    private final AbrasfSigner signer = new AbrasfSigner();
    private final AbrasfSoapEnvelope envelope = new AbrasfSoapEnvelope();
    private final SimuladorAbrasf simulador = new SimuladorAbrasf();
    private final AbrasfRetornoParser parser = new AbrasfRetornoParser();

    @Override
    public TipoProvedor tipo() { return TipoProvedor.ABRASF_2X; }

    @Override
    public ResultadoEmissao emitir(RpsRequest rps, CertificadoLoader.Certificado cert, ProvedorConfig cfg) {
        return emitir(rps, cert, cfg, "AUTORIZADA");
    }

    public ResultadoEmissao emitir(RpsRequest rps, CertificadoLoader.Certificado cert,
                                   ProvedorConfig cfg, String simular) {
        String id = "rps" + rps.numero();
        String xml = builder.montar(rps, cfg.versaoAbrasf(), id);
        var validacao = validator.validar(xml);
        if (!validacao.valido()) {
            throw new IllegalArgumentException("RPS ABRASF inválido: " + validacao.mensagem());
        }
        String assinado = signer.assinar(xml, cert, cfg.algoritmo());
        envelope.envelopar(assinado, cfg.estiloEnvelope()); // montado (lab não transmite)
        String resposta = simulador.responder(simular, rps.numero(), assinado);
        return parser.parse(assinado, resposta);
    }
}
```

- [ ] **Step 4: Implementar `FabricaProvedor.java`**

```java
package br.com.lc.nfse.core.municipal;

import br.com.lc.nfse.core.municipal.abrasf.AbrasfProvedor;
import org.springframework.stereotype.Component;

/** Factory: resolve o adaptador a partir do tipo. Nesta fatia só ABRASF_2X. */
@Component
public class FabricaProvedor {

    public ProvedorMunicipal criar(TipoProvedor tipo) {
        return switch (tipo) {
            case ABRASF_2X -> new AbrasfProvedor();
            case ADN -> throw new IllegalArgumentException(
                    "Cidade é Padrão Nacional (ADN); use POST /v1/nfse");
        };
    }
}
```

- [ ] **Step 5: Rodar — verde**

Run: `./mvnw -q test -Dtest=AbrasfProvedorTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfProvedor.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/FabricaProvedor.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/core/municipal/abrasf/AbrasfProvedorTest.java
git commit -m "feat: AbrasfProvedor (pipeline completo) + FabricaProvedor"
```

---

## Task 8: Registro por IBGE (tabela V5 + ResolvedorProvedor)

**Files:**
- Create: `resources/db/migration/V5__provedores_municipais.sql`
- Create: `core/municipal/registro/ProvedorMunicipalRegistro.java` (entity)
- Create: `core/municipal/registro/ProvedorMunicipalRegistroRepository.java`
- Create: `core/municipal/registro/ResolucaoProvedor.java` (sealed)
- Create: `core/municipal/registro/ResolvedorProvedor.java`
- Test: `src/test/java/br/com/lc/nfse/api/municipal/ResolvedorProvedorTest.java`

**Interfaces:**
- Consumes: `ProvedorConfig`, `TipoProvedor`, enums (Task 2).
- Produces:
  - `sealed interface ResolucaoProvedor permits ProvedorResolvido, CidadeAdn, CidadeNaoSuportada`
    com `record ProvedorResolvido(ProvedorConfig config)`, `record CidadeAdn()`, `record CidadeNaoSuportada(String ibge)`.
  - `ResolvedorProvedor` (`@Service`) com `ResolucaoProvedor resolver(String ibge)`.

- [ ] **Step 1: Migration V5 — tabela + seed de AMOSTRA**

Create `resources/db/migration/V5__provedores_municipais.sql`:

```sql
-- Registro de roteamento por código IBGE (7 dígitos).
create table provedores_municipais (
    codigo_ibge      varchar(7) primary key,
    nome             varchar(120) not null,
    uf               varchar(2)   not null,
    tipo             varchar(20)  not null,   -- ADN | ABRASF_2X
    versao_abrasf    varchar(6),
    url_homolog      varchar(400),
    url_prod         varchar(400),
    estilo_envelope  varchar(30)  not null default 'NFSE_DADOS_MSG',
    algoritmo        varchar(10)  not null default 'SHA1'
);

-- Semeadura de AMOSTRA para o protótipo. O import completo do ACBrNFSeXServicos.ini
-- (5.571 cidades por IBGE) é uma fatia seguinte de engenharia de dados.
insert into provedores_municipais
    (codigo_ibge, nome, uf, tipo, versao_abrasf, url_homolog, url_prod, estilo_envelope, algoritmo) values
    ('4204608', 'Criciuma', 'SC', 'ABRASF_2X', '2.04',
     'https://homologacao.exemplo.gov.br/nfse/ws', 'https://nfse.exemplo.gov.br/ws', 'NFSE_DADOS_MSG', 'SHA1'),
    ('1501808', 'Breves', 'PA', 'ADN', null, null, null, 'NFSE_DADOS_MSG', 'SHA1');
```

- [ ] **Step 2: Escrever o teste do resolvedor (falha)**

Create `src/test/java/br/com/lc/nfse/api/municipal/ResolvedorProvedorTest.java`:

```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.core.municipal.TipoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolucaoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolvedorProvedor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedorProvedorTest extends AbstractPostgresIT {

    @Autowired ResolvedorProvedor resolvedor;

    @Test
    void ibgeAbrasf_resolveComConfig() {
        ResolucaoProvedor r = resolvedor.resolver("4204608");
        assertThat(r).isInstanceOf(ResolucaoProvedor.ProvedorResolvido.class);
        var pr = (ResolucaoProvedor.ProvedorResolvido) r;
        assertThat(pr.config().tipo()).isEqualTo(TipoProvedor.ABRASF_2X);
        assertThat(pr.config().versaoAbrasf()).isEqualTo("2.04");
    }

    @Test
    void ibgeAdn_deflete() {
        assertThat(resolvedor.resolver("1501808")).isInstanceOf(ResolucaoProvedor.CidadeAdn.class);
    }

    @Test
    void ibgeDesconhecido_naoSuportado() {
        assertThat(resolvedor.resolver("3550308")).isInstanceOf(ResolucaoProvedor.CidadeNaoSuportada.class);
    }
}
```

> **Nota:** este teste estende `AbstractPostgresIT`, cujo `@BeforeEach` faz `truncate` — a Task 10 atualiza o truncate para preservar `provedores_municipais` (tabela de referência, semeada por migration). Enquanto a Task 10 não roda, incluir aqui um re-seed no `@BeforeEach` **não** é necessário se o truncate da base **não** listar `provedores_municipais`. Garantir na Task 10 que o truncate NÃO inclui `provedores_municipais`.

- [ ] **Step 3: Rodar — falha**

Run: `./mvnw -q test -Dtest=ResolvedorProvedorTest`
Expected: FAIL (tipos não existem).

- [ ] **Step 4: Criar a entity `ProvedorMunicipalRegistro.java`**

```java
package br.com.lc.nfse.core.municipal.registro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "provedores_municipais")
public class ProvedorMunicipalRegistro {

    @Id
    @Column(name = "codigo_ibge")
    private String codigoIbge;

    private String nome;
    private String uf;
    private String tipo;

    @Column(name = "versao_abrasf")
    private String versaoAbrasf;

    @Column(name = "url_homolog")
    private String urlHomolog;

    @Column(name = "url_prod")
    private String urlProd;

    @Column(name = "estilo_envelope")
    private String estiloEnvelope;

    private String algoritmo;

    protected ProvedorMunicipalRegistro() {}

    public String getCodigoIbge() { return codigoIbge; }
    public String getNome() { return nome; }
    public String getUf() { return uf; }
    public String getTipo() { return tipo; }
    public String getVersaoAbrasf() { return versaoAbrasf; }
    public String getUrlHomolog() { return urlHomolog; }
    public String getUrlProd() { return urlProd; }
    public String getEstiloEnvelope() { return estiloEnvelope; }
    public String getAlgoritmo() { return algoritmo; }
}
```

- [ ] **Step 5: Criar o repository**

```java
package br.com.lc.nfse.core.municipal.registro;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvedorMunicipalRegistroRepository
        extends JpaRepository<ProvedorMunicipalRegistro, String> {
}
```

- [ ] **Step 6: Criar o resultado selado `ResolucaoProvedor.java`**

```java
package br.com.lc.nfse.core.municipal.registro;

import br.com.lc.nfse.core.municipal.ProvedorConfig;

/** Resultado da resolução por IBGE. */
public sealed interface ResolucaoProvedor
        permits ResolucaoProvedor.ProvedorResolvido, ResolucaoProvedor.CidadeAdn,
                ResolucaoProvedor.CidadeNaoSuportada {

    record ProvedorResolvido(ProvedorConfig config) implements ResolucaoProvedor {}
    record CidadeAdn() implements ResolucaoProvedor {}
    record CidadeNaoSuportada(String ibge) implements ResolucaoProvedor {}
}
```

- [ ] **Step 7: Criar `ResolvedorProvedor.java`**

```java
package br.com.lc.nfse.core.municipal.registro;

import br.com.lc.nfse.core.municipal.AlgoritmoAssinatura;
import br.com.lc.nfse.core.municipal.EstiloEnvelope;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.TipoProvedor;
import org.springframework.stereotype.Service;

/** Cérebro de roteamento: dado o código IBGE, decide o modo de emissão. */
@Service
public class ResolvedorProvedor {

    private final ProvedorMunicipalRegistroRepository repo;

    public ResolvedorProvedor(ProvedorMunicipalRegistroRepository repo) {
        this.repo = repo;
    }

    public ResolucaoProvedor resolver(String ibge) {
        return repo.findById(ibge)
                .map(this::mapear)
                .orElseGet(() -> new ResolucaoProvedor.CidadeNaoSuportada(ibge));
    }

    private ResolucaoProvedor mapear(ProvedorMunicipalRegistro reg) {
        TipoProvedor tipo = TipoProvedor.valueOf(reg.getTipo());
        if (tipo == TipoProvedor.ADN) {
            return new ResolucaoProvedor.CidadeAdn();
        }
        ProvedorConfig cfg = new ProvedorConfig(tipo, reg.getVersaoAbrasf(),
                reg.getUrlHomolog(), reg.getUrlProd(),
                EstiloEnvelope.valueOf(reg.getEstiloEnvelope()),
                AlgoritmoAssinatura.valueOf(reg.getAlgoritmo()));
        return new ResolucaoProvedor.ProvedorResolvido(cfg);
    }
}
```

- [ ] **Step 8: Rodar — verde**

Run: `./mvnw -q test -Dtest=ResolvedorProvedorTest`
Expected: PASS (3 testes).

- [ ] **Step 9: Commit**

```bash
git add nfse-nacional/src/main/resources/db/migration/V5__provedores_municipais.sql \
        nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/registro \
        nfse-nacional/src/test/java/br/com/lc/nfse/api/municipal/ResolvedorProvedorTest.java
git commit -m "feat: registro provedores_municipais + ResolvedorProvedor (roteamento por IBGE)"
```

---

## Task 9: Persistência da emissão municipal (V6 + entity + repo)

**Files:**
- Create: `resources/db/migration/V6__emissoes_municipais.sql`
- Create: `api/municipal/EmissaoMunicipal.java` (entity)
- Create: `api/municipal/EmissaoMunicipalRepository.java`
- Test: `src/test/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalRepositoryTest.java`

**Interfaces:**
- Consumes: `StatusEmissaoMunicipal` (Task 2).
- Produces:
  - `EmissaoMunicipal` com factories `autorizada(...)` / `rejeitada(...)` e getters.
  - `EmissaoMunicipalRepository` com `Optional<EmissaoMunicipal> findByIdAndContaId(UUID id, UUID contaId)`
    e `long countByEmpresaId(UUID empresaId)`.

- [ ] **Step 1: Migration V6**

Create `resources/db/migration/V6__emissoes_municipais.sql`:

```sql
create table emissoes_municipais (
    id                  uuid primary key,
    conta_id            uuid not null,
    empresa_id          uuid not null,
    codigo_ibge         varchar(7) not null,
    provedor            varchar(20) not null,
    status              varchar(20) not null,
    numero_nfse         varchar(30),
    codigo_verificacao  varchar(60),
    protocolo           varchar(60),
    mensagens           text,
    xml_enviado         text,
    xml_retorno         text,
    criado_em           timestamptz not null
);

create index idx_emissoes_municipais_conta on emissoes_municipais (conta_id);
```

- [ ] **Step 2: Escrever o teste do repo (falha)**

```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmissaoMunicipalRepositoryTest extends AbstractPostgresIT {

    @Autowired EmissaoMunicipalRepository repo;

    @Test
    void salvaEBuscaEscopadaPorConta() {
        UUID conta = UUID.randomUUID();
        UUID empresa = UUID.randomUUID();
        EmissaoMunicipal e = EmissaoMunicipal.autorizada(conta, empresa, "4204608", "ABRASF_2X",
                "1", "ABC123", null, "<xml/>", "<resp/>", OffsetDateTime.now());
        repo.save(e);

        assertThat(repo.findByIdAndContaId(e.getId(), conta)).isPresent();
        assertThat(repo.findByIdAndContaId(e.getId(), UUID.randomUUID())).isEmpty();
        assertThat(repo.countByEmpresaId(empresa)).isEqualTo(1);
    }

    @Test
    void rejeitadaGuardaMensagens() {
        UUID conta = UUID.randomUUID();
        EmissaoMunicipal e = EmissaoMunicipal.rejeitada(conta, UUID.randomUUID(), "4204608", "ABRASF_2X",
                List.of("E9999: rejeição"), "<xml/>", "<resp/>", OffsetDateTime.now());
        repo.save(e);
        assertThat(repo.findByIdAndContaId(e.getId(), conta)).get()
                .extracting(EmissaoMunicipal::getStatus).isEqualTo(StatusEmissaoMunicipal.REJEITADA);
    }
}
```

- [ ] **Step 3: Rodar — falha**

Run: `./mvnw -q test -Dtest=EmissaoMunicipalRepositoryTest`
Expected: FAIL.

- [ ] **Step 4: Criar a entity `EmissaoMunicipal.java`** (mensagens serializadas com `\n`)

```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Uma emissão municipal (ABRASF), no protótipo simulada. Escopada por Conta. */
@Entity
@Table(name = "emissoes_municipais")
public class EmissaoMunicipal {

    @Id
    private UUID id;

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "codigo_ibge", nullable = false)
    private String codigoIbge;

    @Column(nullable = false)
    private String provedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEmissaoMunicipal status;

    @Column(name = "numero_nfse")
    private String numeroNfse;

    @Column(name = "codigo_verificacao")
    private String codigoVerificacao;

    private String protocolo;

    @Column
    private String mensagens;

    @Column(name = "xml_enviado")
    private String xmlEnviado;

    @Column(name = "xml_retorno")
    private String xmlRetorno;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected EmissaoMunicipal() {}

    public static EmissaoMunicipal autorizada(UUID contaId, UUID empresaId, String codigoIbge, String provedor,
                                              String numeroNfse, String codigoVerificacao, String protocolo,
                                              String xmlEnviado, String xmlRetorno, OffsetDateTime agora) {
        EmissaoMunicipal e = base(contaId, empresaId, codigoIbge, provedor, xmlEnviado, xmlRetorno, agora);
        e.status = StatusEmissaoMunicipal.AUTORIZADA;
        e.numeroNfse = numeroNfse;
        e.codigoVerificacao = codigoVerificacao;
        e.protocolo = protocolo;
        return e;
    }

    public static EmissaoMunicipal rejeitada(UUID contaId, UUID empresaId, String codigoIbge, String provedor,
                                             List<String> mensagens, String xmlEnviado, String xmlRetorno,
                                             OffsetDateTime agora) {
        EmissaoMunicipal e = base(contaId, empresaId, codigoIbge, provedor, xmlEnviado, xmlRetorno, agora);
        e.status = StatusEmissaoMunicipal.REJEITADA;
        e.mensagens = mensagens == null ? null : String.join("\n", mensagens);
        return e;
    }

    private static EmissaoMunicipal base(UUID contaId, UUID empresaId, String codigoIbge, String provedor,
                                         String xmlEnviado, String xmlRetorno, OffsetDateTime agora) {
        EmissaoMunicipal e = new EmissaoMunicipal();
        e.id = UUID.randomUUID();
        e.contaId = contaId;
        e.empresaId = empresaId;
        e.codigoIbge = codigoIbge;
        e.provedor = provedor;
        e.xmlEnviado = xmlEnviado;
        e.xmlRetorno = xmlRetorno;
        e.criadoEm = agora;
        return e;
    }

    public List<String> mensagensLista() {
        return (mensagens == null || mensagens.isBlank()) ? List.of() : List.of(mensagens.split("\n"));
    }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigoIbge() { return codigoIbge; }
    public String getProvedor() { return provedor; }
    public StatusEmissaoMunicipal getStatus() { return status; }
    public String getNumeroNfse() { return numeroNfse; }
    public String getCodigoVerificacao() { return codigoVerificacao; }
    public String getProtocolo() { return protocolo; }
    public String getXmlEnviado() { return xmlEnviado; }
    public String getXmlRetorno() { return xmlRetorno; }
}
```

- [ ] **Step 5: Criar o repository**

```java
package br.com.lc.nfse.api.municipal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmissaoMunicipalRepository extends JpaRepository<EmissaoMunicipal, UUID> {
    Optional<EmissaoMunicipal> findByIdAndContaId(UUID id, UUID contaId);
    long countByEmpresaId(UUID empresaId);
}
```

- [ ] **Step 6: Rodar — verde**

Run: `./mvnw -q test -Dtest=EmissaoMunicipalRepositoryTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add nfse-nacional/src/main/resources/db/migration/V6__emissoes_municipais.sql \
        nfse-nacional/src/main/java/br/com/lc/nfse/api/municipal/EmissaoMunicipal.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalRepository.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalRepositoryTest.java
git commit -m "feat: persistência emissoes_municipais (entity + repo + V6)"
```

---

## Task 10: Serviço + Controller (wiring da rota /v1/nfse-municipal)

**Files:**
- Create: `api/municipal/dto/EmitirNfseMunicipalRequest.java`
- Create: `api/municipal/dto/EmissaoMunicipalResponse.java`
- Create: `api/municipal/EmissaoMunicipalService.java`
- Create: `api/municipal/MunicipalController.java`
- Modify: `src/test/java/br/com/lc/nfse/api/AbstractPostgresIT.java` (truncate)

**Interfaces:**
- Consumes: `ResolvedorProvedor`/`ResolucaoProvedor` (Task 8), `FabricaProvedor`/`AbrasfProvedor` (Task 7),
  `EmpresaRepository`/`Empresa` (existentes), `CertificadoProvider` (existente), `EmissaoMunicipalRepository` (Task 9),
  `TenantPrincipal`/`Ambiente` (existentes).
- Produces: `EmissaoMunicipalService.emitir(TenantPrincipal, EmitirNfseMunicipalRequest) -> EmissaoMunicipalResponse`
  e `consultar(UUID contaId, UUID id) -> EmissaoMunicipalResponse`.

- [ ] **Step 1: Atualizar `AbstractPostgresIT` truncate** (NÃO truncar `provedores_municipais` — é tabela de referência semeada por migration)

Trocar a linha do `truncate` em `limparBanco()` por:
```java
jdbcTemplate.execute(
    "truncate table emissoes_municipais, emissoes, api_keys, empresas, contas restart identity cascade");
```

- [ ] **Step 2: Criar os DTOs**

`api/municipal/dto/EmitirNfseMunicipalRequest.java`:
```java
package br.com.lc.nfse.api.municipal.dto;

import java.util.UUID;

/** Emissão municipal: empresa (prestador+regime) + IBGE de incidência + dados do serviço. */
public record EmitirNfseMunicipalRequest(UUID empresaId, String codigoMunicipioIbge,
                                         Servico servico, String simular) {

    public record Servico(String valorServicos, String itemListaServico, String discriminacao,
                          int issRetido, int exigibilidadeIss) {}
}
```
`api/municipal/dto/EmissaoMunicipalResponse.java`:
```java
package br.com.lc.nfse.api.municipal.dto;

import br.com.lc.nfse.api.municipal.EmissaoMunicipal;

import java.util.List;
import java.util.UUID;

public record EmissaoMunicipalResponse(UUID id, String status, String numeroNfse, String codigoVerificacao,
                                       String protocolo, String xmlEnviado, List<String> mensagens) {

    public static EmissaoMunicipalResponse de(EmissaoMunicipal e) {
        return new EmissaoMunicipalResponse(e.getId(), e.getStatus().name(), e.getNumeroNfse(),
                e.getCodigoVerificacao(), e.getProtocolo(), e.getXmlEnviado(), e.mensagensLista());
    }
}
```

- [ ] **Step 3: Escrever o teste de integração (falha) — ver Task 11**

O teste ponta-a-ponta completo é a Task 11. Aqui, apenas garantir compilação criando o serviço/controller e rodar a Task 11 depois. Prosseguir para os steps de implementação.

- [ ] **Step 4: Criar `EmissaoMunicipalService.java`**

```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.emissao.ProducaoIndisponivelException;
import br.com.lc.nfse.api.error.ConflitoException;
import br.com.lc.nfse.api.error.NaoEncontradoException;
import br.com.lc.nfse.api.municipal.dto.EmissaoMunicipalResponse;
import br.com.lc.nfse.api.municipal.dto.EmitirNfseMunicipalRequest;
import br.com.lc.nfse.api.tenant.Empresa;
import br.com.lc.nfse.api.tenant.EmpresaRepository;
import br.com.lc.nfse.api.tenant.OpSimplesNacional;
import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.FabricaProvedor;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.RpsRequest;
import br.com.lc.nfse.core.municipal.StatusEmissaoMunicipal;
import br.com.lc.nfse.core.municipal.abrasf.AbrasfProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolucaoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolvedorProvedor;
import br.com.lc.nfse.web.CertificadoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Orquestra a emissão municipal: resolve por IBGE -> fabrica provedor -> emite -> persiste. */
@Service
public class EmissaoMunicipalService {

    private static final Logger log = LoggerFactory.getLogger(EmissaoMunicipalService.class);
    private static final ZoneOffset BR = ZoneOffset.of("-03:00");

    private final EmpresaRepository empresaRepository;
    private final EmissaoMunicipalRepository emissaoRepository;
    private final ResolvedorProvedor resolvedor;
    private final FabricaProvedor fabrica;
    private final CertificadoProvider certificadoProvider;

    public EmissaoMunicipalService(EmpresaRepository empresaRepository,
                                   EmissaoMunicipalRepository emissaoRepository,
                                   ResolvedorProvedor resolvedor, FabricaProvedor fabrica,
                                   CertificadoProvider certificadoProvider) {
        this.empresaRepository = empresaRepository;
        this.emissaoRepository = emissaoRepository;
        this.resolvedor = resolvedor;
        this.fabrica = fabrica;
        this.certificadoProvider = certificadoProvider;
    }

    @Transactional
    public EmissaoMunicipalResponse emitir(TenantPrincipal principal, EmitirNfseMunicipalRequest req) {
        if (principal.ambiente() != Ambiente.SANDBOX) {
            throw new ProducaoIndisponivelException("Emissão municipal em produção ainda não disponível");
        }
        ProvedorConfig cfg = switch (resolvedor.resolver(req.codigoMunicipioIbge())) {
            case ResolucaoProvedor.ProvedorResolvido pr -> pr.config();
            case ResolucaoProvedor.CidadeAdn ignored ->
                    throw new ConflitoException("Cidade é Padrão Nacional (ADN); use POST /v1/nfse");
            case ResolucaoProvedor.CidadeNaoSuportada ns -> {
                log.info("IBGE não suportado solicitado: {}", ns.ibge()); // sinal de demanda
                throw new IllegalArgumentException("Município ainda não suportado: " + ns.ibge());
            }
        };

        Empresa empresa = empresaRepository.findByIdAndContaId(req.empresaId(), principal.contaId())
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada: " + req.empresaId()));

        String numero = String.valueOf(emissaoRepository.countByEmpresaId(empresa.getId()) + 1);
        RpsRequest rps = montarRps(empresa, req, numero);

        CertificadoLoader.Certificado cert = certificadoProvider.obter().orElseThrow(
                () -> new IllegalStateException("Certificado de assinatura do sandbox não configurado"));

        AbrasfProvedor provedor = (AbrasfProvedor) fabrica.criar(cfg.tipo());
        ResultadoEmissao r = provedor.emitir(rps, cert, cfg, req.simular());

        OffsetDateTime agora = OffsetDateTime.now();
        EmissaoMunicipal emissao = r.status() == StatusEmissaoMunicipal.AUTORIZADA
                ? EmissaoMunicipal.autorizada(principal.contaId(), empresa.getId(), req.codigoMunicipioIbge(),
                    cfg.tipo().name(), r.numeroNfse(), r.codigoVerificacao(), r.protocolo(),
                    r.xmlEnviado(), r.xmlRetorno(), agora)
                : EmissaoMunicipal.rejeitada(principal.contaId(), empresa.getId(), req.codigoMunicipioIbge(),
                    cfg.tipo().name(), r.mensagens(), r.xmlEnviado(), r.xmlRetorno(), agora);
        emissaoRepository.save(emissao);
        return EmissaoMunicipalResponse.de(emissao);
    }

    @Transactional(readOnly = true)
    public EmissaoMunicipalResponse consultar(UUID contaId, UUID id) {
        return emissaoRepository.findByIdAndContaId(id, contaId).map(EmissaoMunicipalResponse::de)
                .orElseThrow(() -> new NaoEncontradoException("Emissão não encontrada: " + id));
    }

    private RpsRequest montarRps(Empresa e, EmitirNfseMunicipalRequest req, String numero) {
        LocalDate hoje = OffsetDateTime.now(BR).toLocalDate();
        int optante = e.getOpSimplesNacional() == OpSimplesNacional.NAO_OPTANTE ? 2 : 1;
        var s = req.servico();
        return new RpsRequest(numero, "1", "1", hoje, hoje,
                s.valorServicos(), s.issRetido(), s.itemListaServico(), s.discriminacao(),
                req.codigoMunicipioIbge(), s.exigibilidadeIss(),
                e.getCnpj(), e.getInscricaoMunicipal() == null ? "0" : e.getInscricaoMunicipal(),
                optante, 2);
    }
}
```

> **Enum confirmado:** `OpSimplesNacional` = `NAO_OPTANTE(1)`, `MEI(2)`, `ME_EPP(3)`. A comparação `== NAO_OPTANTE → 2 (Não)`, senão `1 (Sim)`, está correta para o `OptanteSimplesNacional` do ABRASF (1=Sim, 2=Não).

- [ ] **Step 5: Criar `MunicipalController.java`**

```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.municipal.dto.EmissaoMunicipalResponse;
import br.com.lc.nfse.api.municipal.dto.EmitirNfseMunicipalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Emissão de NFS-e municipal (legado / fora do ADN). Rota separada do Padrão Nacional. */
@RestController
@RequestMapping("/v1/nfse-municipal")
public class MunicipalController {

    private final EmissaoMunicipalService service;

    public MunicipalController(EmissaoMunicipalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmissaoMunicipalResponse> emitir(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestBody EmitirNfseMunicipalRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.emitir(principal, req));
    }

    @GetMapping("/{id}")
    public EmissaoMunicipalResponse consultar(@AuthenticationPrincipal TenantPrincipal principal,
                                              @PathVariable UUID id) {
        return service.consultar(principal.contaId(), id);
    }
}
```

> **Segurança:** `/v1/**` já exige `ROLE_TENANT` no `SecurityConfig` existente — a rota nova é coberta automaticamente. Nenhuma mudança em `SecurityConfig`.

- [ ] **Step 6: Compilar**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add nfse-nacional/src/main/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalService.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/api/municipal/MunicipalController.java \
        nfse-nacional/src/main/java/br/com/lc/nfse/api/municipal/dto \
        nfse-nacional/src/test/java/br/com/lc/nfse/api/AbstractPostgresIT.java
git commit -m "feat: rota POST /v1/nfse-municipal (serviço + controller + DTOs)"
```

---

## Task 11: Teste de integração ponta-a-ponta

**Files:**
- Create: `src/test/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalIT.java`

**Interfaces:**
- Consumes: toda a stack (Tasks 1-10) + infra de teste (`AbstractPostgresIT`, `ContaRepository`, `ApiKeyRepository`, `ApiKeyService`, `EmpresaRepository`).

- [ ] **Step 1: Escrever o teste de integração**

```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.ApiKey;
import br.com.lc.nfse.api.auth.ApiKeyRepository;
import br.com.lc.nfse.api.auth.ApiKeyService;
import br.com.lc.nfse.api.municipal.dto.EmissaoMunicipalResponse;
import br.com.lc.nfse.api.tenant.Conta;
import br.com.lc.nfse.api.tenant.ContaRepository;
import br.com.lc.nfse.api.tenant.Empresa;
import br.com.lc.nfse.api.tenant.EmpresaRepository;
import br.com.lc.nfse.api.tenant.OpSimplesNacional;
import br.com.lc.nfse.api.tenant.RegimeEspecialTributacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmissaoMunicipalIT extends AbstractPostgresIT {

    @Value("${local.server.port}") int port;
    @Autowired ContaRepository contaRepo;
    @Autowired ApiKeyRepository keyRepo;
    @Autowired ApiKeyService keySvc;
    @Autowired EmpresaRepository empresaRepo;

    private RestClient client() { return RestClient.create("http://localhost:" + port); }

    private record Setup(String chave, UUID empresaId) {}

    private Setup setup(String nome, String chave, Ambiente ambiente) {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta c = contaRepo.save(Conta.nova(nome, agora));
        keyRepo.save(ApiKey.nova(c.getId(), ambiente, keySvc.hash(chave), chave.substring(0, 14), agora));
        Empresa e = empresaRepo.save(Empresa.nova(c.getId(), "11222333000181", nome + " Ltda", "123",
                "4204608", OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora));
        return new Setup(chave, e.getId());
    }

    private String json(UUID empresaId, String ibge, String simular) {
        return """
            {"empresaId":"%s","codigoMunicipioIbge":"%s",
             "servico":{"valorServicos":"1500.00","itemListaServico":"01.01",
                        "discriminacao":"Consultoria em TI","issRetido":2,"exigibilidadeIss":1},
             "simular":"%s"}
            """.formatted(empresaId, ibge, simular);
    }

    private HttpStatusCode postStatus(Setup s, String ibge, String simular) {
        return client().post().uri("/v1/nfse-municipal").header("X-Api-Key", s.chave())
                .contentType(MediaType.APPLICATION_JSON).body(json(s.empresaId(), ibge, simular))
                .exchange((req, res) -> res.getStatusCode());
    }

    @Test
    void emiteMunicipalAutorizadaEConsulta() {
        Setup s = setup("Cli Mun", "sk_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", Ambiente.SANDBOX);

        EmissaoMunicipalResponse emitida = client().post().uri("/v1/nfse-municipal")
                .header("X-Api-Key", s.chave()).contentType(MediaType.APPLICATION_JSON)
                .body(json(s.empresaId(), "4204608", "AUTORIZADA"))
                .retrieve().toEntity(EmissaoMunicipalResponse.class).getBody();

        assertEquals("AUTORIZADA", emitida.status());
        assertThat(emitida.numeroNfse()).isNotBlank();
        assertThat(emitida.xmlEnviado()).contains("<Signature").contains("GerarNfseEnvio");

        var consulta = client().get().uri("/v1/nfse-municipal/" + emitida.id())
                .header("X-Api-Key", s.chave()).retrieve().toEntity(EmissaoMunicipalResponse.class);
        assertEquals(200, consulta.getStatusCode().value());
        assertEquals("AUTORIZADA", consulta.getBody().status());
    }

    @Test
    void ibgeAdnRetorna409() {
        Setup s = setup("Cli ADN", "sk_test_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", Ambiente.SANDBOX);
        assertEquals(409, postStatus(s, "1501808", "AUTORIZADA").value());
    }

    @Test
    void ibgeDesconhecidoRetorna422() {
        Setup s = setup("Cli Desc", "sk_test_cccccccccccccccccccccccccccccccc", Ambiente.SANDBOX);
        assertEquals(422, postStatus(s, "3550308", "AUTORIZADA").value());
    }

    @Test
    void crossTenantConsultaRetorna404() {
        Setup a = setup("Conta A", "sk_test_dddddddddddddddddddddddddddddddd", Ambiente.SANDBOX);
        Setup b = setup("Conta B", "sk_test_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", Ambiente.SANDBOX);
        UUID idA = client().post().uri("/v1/nfse-municipal").header("X-Api-Key", a.chave())
                .contentType(MediaType.APPLICATION_JSON).body(json(a.empresaId(), "4204608", "AUTORIZADA"))
                .retrieve().toEntity(EmissaoMunicipalResponse.class).getBody().id();

        HttpStatusCode st = client().get().uri("/v1/nfse-municipal/" + idA).header("X-Api-Key", b.chave())
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(404, st.value());
    }

    @Test
    void chaveProducaoRetorna501() {
        Setup s = setup("Cli Prod", "sk_live_ffffffffffffffffffffffffffffffff", Ambiente.PRODUCAO);
        assertEquals(501, postStatus(s, "4204608", "AUTORIZADA").value());
    }
}
```

- [ ] **Step 2: Rodar — deve ficar verde**

Run: `./mvnw -q test -Dtest=EmissaoMunicipalIT`
Expected: PASS (5 testes). Se o 409/422 falhar, conferir o mapeamento em `ApiExceptionHandler` (ConflitoException→409, IllegalArgumentException→422 — já existentes).

- [ ] **Step 3: Suíte completa — nada regrediu**

Run: `./mvnw -q test`
Expected: PASS (ADN + municipal). Cobertura do pacote `municipal/` ≥ 80%.

- [ ] **Step 4: Commit**

```bash
git add nfse-nacional/src/test/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalIT.java
git commit -m "test: emissão municipal ponta-a-ponta (202, 409 ADN, 422 desconhecido, 404, 501)"
```

---

## Self-Review (feita na escrita do plano)

**Cobertura do spec:**
- §3 arquitetura (pacotes isolados `core/municipal` + `api/municipal`) → Tasks 2-10. ✔
- §3 extração `core/xsd/SanitizadorAncoras` → Task 1. ✔
- §4 fluxo (resolve→fabrica→emite→persiste; 409/422/501) → Tasks 8, 10, 11. ✔
- §5 contratos (`ProvedorMunicipal`, `RpsRequest`, `ProvedorConfig`, `ResultadoEmissao`) → Task 2. ✔
- §6 erros (SHA1+secureValidation; XSD 422; ADN 409; desconhecido 422; sinal de demanda) → Tasks 4, 10, 11. ✔
- §7 reúso (XSD oficial, algoritmo por config) → Tasks 1, 4, 8. ✔
- §8 persistência (V5 provedores, V6 emissoes) → Tasks 8, 9. ✔
- §9 testes (âncora XSD, assinatura, resolvedor, integração) → Tasks 3, 4, 8, 11. ✔

**Placeholders:** nenhum "TBD/TODO"; todo step tem código real.

**Consistência de tipos:** `RpsRequest`, `ProvedorConfig`, `ResultadoEmissao`, `ResolucaoProvedor.*`, `EmissaoMunicipal.autorizada/rejeitada`, `ProvedorMunicipal.emitir` usados com as mesmas assinaturas em todas as tasks.

**Dependências do código existente — verificadas contra os arquivos:**
- `CertificadoLoader.carregar(InputStream, char[])` — confirmado (não há `carregarDeClasspath`); testes usam `/certs/teste.p12`/`changeit`.
- `OpSimplesNacional` = `NAO_OPTANTE(1)/MEI(2)/ME_EPP(3)` — confirmado.
- `ApiExceptionHandler`: `ConflitoException→409`, `IllegalArgumentException→422`, `NaoEncontradoException→404`, `ProducaoIndisponivelException→501` — confirmado (reusados, sem tocar no handler).
- `/v1/**` já protegido por `ROLE_TENANT` no `SecurityConfig` — a rota nova é coberta sem alteração.

**Risco residual (do domínio, não do código):** se o XSD reprovar o XML montado (Task 3), o validador aponta o elemento a ajustar — o teste-âncora é justamente a rede de segurança.
```
