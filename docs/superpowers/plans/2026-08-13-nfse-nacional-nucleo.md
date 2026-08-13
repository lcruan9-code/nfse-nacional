# NFS-e Padrão Nacional — Núcleo (Protótipo #1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o núcleo offline que gera uma DPS assinada (XMLDSig), válida contra o XSD oficial da Receita, e empacotada (gzip+base64) — pronta para transmitir ao ADN quando houver um certificado A1.

**Architecture:** Spring Boot 3.x com duas camadas isoladas — um `core` fiscal sem dependência de web (DpsBuilder → DpsValidator → DpsSigner → DpsPackager) e uma casca `web` fina (`POST /dps/preview`). O modelo da DPS é gerado por JAXB a partir do XSD oficial, que vira a fonte da verdade dos campos.

**Tech Stack:** Java 17 (JDK em `C:\Program Files\Java\jdk-17`), Spring Boot 3.3.x, Maven via Wrapper (`mvnw`), JAXB (Jakarta), `java.xml.crypto` nativo para XMLDSig, `java.util.zip` + `Base64` para empacotamento, JUnit 5.

## Global Constraints

- **Build no JDK 17**, nunca no Java 8 padrão do sistema. **Não alterar o `JAVA_HOME` global** (ele serve os migradores NetBeans/Ant). O JDK 17 é apontado só por sessão de build.
- **Maven só via Wrapper** (`mvnw.cmd`). Não instalar Maven global.
- **Sem transmissão real** neste protótipo (sem A1). Nada de mTLS/POST ao governo.
- **`core` não depende de Spring/web.** Beans simples, testáveis por JUnit puro.
- **TDD:** teste falhando primeiro, implementação mínima depois.
- **Commits locais apenas.** `git` local é a fonte da verdade; **nunca fazer push** (a menos que o Ruan peça).
- **Certificado real nunca é commitado.** Só um autoassinado de teste, marcado como não-ICP.
- **Estilo:** arquivos pequenos e focados, imutabilidade nos DTOs, `camelCase`, erros tratados explicitamente.
- **Raiz do projeto Maven:** `C:\PROJETOS\Ruan\NFS-e\nfse-nacional\`. Repositório git em `C:\PROJETOS\Ruan\NFS-e\`.

---

## Roteiro de Fases

| Fase | Entrega | Status neste plano |
|------|---------|--------------------|
| **0** | Scaffold Spring Boot + wrapper + JDK 17, build verde | **Detalhada (executável agora)** |
| **1** | Schema XSD oficial fixado + algoritmo de assinatura confirmado + sample oficial validando | **Detalhada (executável agora)** |
| 2 | Modelo JAXB gerado do XSD | Roteiro (detalhar pós-Fase 1) |
| 3 | `DpsPackager` (gzip + base64) | Roteiro (detalhar pós-Fase 1) |
| 4 | `CertificadoLoader` + certificado de teste autoassinado | Roteiro (detalhar pós-Fase 1) |
| 5 | `DpsValidator` (validação XSD) | Roteiro (detalhar pós-Fase 1) |
| 6 | `DpsSigner` (XMLDSig enveloped) | Roteiro (detalhar pós-Fase 1) |
| 7 | `DpsBuilder` (DTO → DPS XML) | Roteiro (detalhar pós-Fase 1) |
| 8 | `DpsPreviewController` (`POST /dps/preview`) + tratamento de erros | Roteiro (detalhar pós-Fase 1) |

**Motivo do staging:** as Fases 2, 6 e 7 dependem da estrutura exata da DPS e do algoritmo de assinatura, ambos definidos pelo XSD/MOC oficiais obtidos na Fase 1. Detalhar código exato dessas fases antes da Fase 1 seria adivinhar regra fiscal.

---

## FASE 0 — Ambiente e Scaffold

### Task 0.1: Scaffold do projeto Spring Boot com Maven Wrapper

**Files:**
- Create: `C:\PROJETOS\Ruan\NFS-e\nfse-nacional\` (projeto gerado)
- Create: `C:\PROJETOS\Ruan\NFS-e\.gitignore`

**Interfaces:**
- Consumes: nada (primeira task).
- Produces: projeto Maven com `mvnw.cmd`, `pom.xml`, `src/main/java/br/com/lc/nfse/NfseNacionalApplication.java`, e o teste padrão `NfseNacionalApplicationTests.contextLoads()`.

- [ ] **Step 1: Gerar o esqueleto via Spring Initializr**

Na pasta `C:\PROJETOS\Ruan\NFS-e\`, rodar (PowerShell):

```powershell
curl.exe -s "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=3.3.5&javaVersion=17&groupId=br.com.lc&artifactId=nfse-nacional&name=nfse-nacional&packageName=br.com.lc.nfse&dependencies=web" -o nfse-nacional.zip
Expand-Archive -Path nfse-nacional.zip -DestinationPath nfse-nacional -Force
Remove-Item nfse-nacional.zip
```

Expected: pasta `nfse-nacional\` com `mvnw.cmd`, `pom.xml`, `src\main\java\br\com\lc\nfse\NfseNacionalApplication.java`.

- [ ] **Step 2: Apontar o JDK 17 só nesta sessão**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Verificar que o wrapper enxerga o Java 17:

```powershell
cd C:\PROJETOS\Ruan\NFS-e\nfse-nacional
.\mvnw.cmd -v
```

Expected: linha `Java version: 17.0.12`.

- [ ] **Step 3: Rodar o teste padrão (deve passar)**

```powershell
.\mvnw.cmd -q test
```

Expected: BUILD SUCCESS, `NfseNacionalApplicationTests.contextLoads` verde. (Primeira execução baixa o Maven e as dependências — requer internet.)

- [ ] **Step 4: Inicializar git local e criar `.gitignore`**

Criar `C:\PROJETOS\Ruan\NFS-e\.gitignore` com:

```gitignore
# build
target/
!.mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
nbproject/private/

# certificados — NUNCA versionar cert real
*.pfx
*.jks
# exceção: certificado de teste autoassinado (Fase 4)
!nfse-nacional/src/test/resources/certs/teste.p12

# SO
Thumbs.db
```

Rodar na pasta `C:\PROJETOS\Ruan\NFS-e\`:

```powershell
cd C:\PROJETOS\Ruan\NFS-e
git init
git add .
git commit -m "chore: scaffold Spring Boot 3.3 nfse-nacional com Maven wrapper (Java 17)"
```

Expected: commit criado localmente. **Sem push.**

---

### Task 0.2: Estrutura de pacotes e profiles de ambiente

**Files:**
- Create: `nfse-nacional/src/main/java/br/com/lc/nfse/core/package-info.java`
- Create: `nfse-nacional/src/main/java/br/com/lc/nfse/web/package-info.java`
- Modify: `nfse-nacional/src/main/resources/application.properties` → substituir por `application.yml`
- Create: `nfse-nacional/src/main/resources/application-homolog.yml`
- Create: `nfse-nacional/src/main/resources/application-prod.yml`
- Test: `nfse-nacional/src/test/java/br/com/lc/nfse/ProfilesSmokeTest.java`

**Interfaces:**
- Consumes: projeto da Task 0.1.
- Produces: pacotes `br.com.lc.nfse.core` e `br.com.lc.nfse.web`; propriedade `nfse.ambiente` resolvida por profile (`homolog` → `2`, `prod` → `1`).

- [ ] **Step 1: Escrever o teste falhando**

`nfse-nacional/src/test/java/br/com/lc/nfse/ProfilesSmokeTest.java`:

```java
package br.com.lc.nfse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("homolog")
class ProfilesSmokeTest {

    @Value("${nfse.ambiente}")
    int ambiente;

    @Test
    void homologResolveAmbiente2() {
        assertEquals(2, ambiente);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
.\mvnw.cmd -q -Dtest=ProfilesSmokeTest test
```

Expected: FAIL — propriedade `nfse.ambiente` não existe.

- [ ] **Step 3: Criar os YAMLs de profile**

Apagar `application.properties`. Criar `application.yml`:

```yaml
spring:
  application:
    name: nfse-nacional
  profiles:
    active: homolog
```

`application-homolog.yml`:

```yaml
nfse:
  ambiente: 2   # 2 = homologacao (Producao Restrita)
  adn:
    base-url: "https://sefin.producaorestrita.nfse.gov.br"  # confirmar na Fase 1
```

`application-prod.yml`:

```yaml
nfse:
  ambiente: 1   # 1 = producao
  adn:
    base-url: "https://sefin.nfse.gov.br"  # confirmar na Fase 1
```

- [ ] **Step 4: Criar os `package-info.java`**

`core/package-info.java`:

```java
/** Núcleo fiscal da NFS-e Nacional. NÃO depende de Spring/web. */
package br.com.lc.nfse.core;
```

`web/package-info.java`:

```java
/** Casca web fina que expõe o núcleo via REST. */
package br.com.lc.nfse.web;
```

- [ ] **Step 5: Rodar e ver passar**

```powershell
.\mvnw.cmd -q -Dtest=ProfilesSmokeTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add .
git commit -m "feat: estrutura de pacotes core/web e profiles homolog/prod"
```

---

## FASE 1 — Schema oficial e algoritmo de assinatura

> Esta fase **não escreve código de produção** — ela resolve as 3 perguntas em aberto do spec e coloca os artefatos oficiais no projeto. É o que desbloqueia as Fases 2–8.

### Task 1.1: Obter e fixar o pacote de XSDs oficial do ADN

**Files:**
- Create: `nfse-nacional/src/main/resources/schemas/` (XSDs oficiais)
- Create: `nfse-nacional/src/main/resources/schemas/DECISOES.md`

**Interfaces:**
- Produces: XSD da DPS + dependências, e um registro escrito da versão do schema.

- [ ] **Step 1: Localizar o pacote oficial**

Acessar a área de documentação/schemas do Portal Nacional (`https://www.nfse.gov.br`), seção "Documentação Técnica / Layout e Schemas". Baixar o pacote de **XSDs da DPS** (Declaração de Prestação de Serviços) da versão vigente.

- [ ] **Step 2: Colocar os XSDs no projeto**

Extrair todos os `.xsd` para `nfse-nacional/src/main/resources/schemas/`, preservando os nomes originais (o XSD principal da DPS costuma importar tipos de XSDs auxiliares — trazer todos).

- [ ] **Step 3: Registrar a versão em `DECISOES.md`**

Criar `schemas/DECISOES.md` anotando: versão do layout/schema, data do download, URL de origem, e o nome do arquivo XSD raiz da DPS.

- [ ] **Step 4: Commit**

```powershell
git add nfse-nacional/src/main/resources/schemas
git commit -m "chore: fixar XSDs oficiais da DPS (NFS-e Nacional) + DECISOES.md"
```

### Task 1.2: Confirmar assinatura, canonicalização e ponto de âncora

**Files:**
- Modify: `nfse-nacional/src/main/resources/schemas/DECISOES.md`

- [ ] **Step 1: Ler o MOC / manual da DPS**

No manual oficial (MOC da NFS-e Nacional), localizar a seção de **assinatura digital da DPS** e registrar em `DECISOES.md`:
- algoritmo de assinatura (ex.: `RSA-SHA1` ou `RSA-SHA256`);
- método de canonicalização (C14N — exclusiva ou não);
- qual elemento recebe o atributo `Id` e é referenciado pela assinatura (tipicamente `infDPS`);
- se a assinatura é `enveloped`.

- [ ] **Step 2: Registrar o endpoint (uso futuro)**

Anotar também o **path do endpoint SEFIN** de registro da DPS e o formato do corpo (o campo que carrega o gzip+base64), para o passo futuro de transmissão.

- [ ] **Step 3: Commit**

```powershell
git add nfse-nacional/src/main/resources/schemas/DECISOES.md
git commit -m "docs: confirmar algoritmo de assinatura e endpoint do ADN em DECISOES.md"
```

### Task 1.3: Sample oficial validando contra o XSD (smoke de sanidade)

**Files:**
- Create: `nfse-nacional/src/test/resources/fixtures/dps-exemplo-oficial.xml`
- Test: `nfse-nacional/src/test/java/br/com/lc/nfse/SchemaSanityTest.java`

**Interfaces:**
- Produces: prova de que os XSDs baixados são internamente consistentes e que um DPS de exemplo oficial valida. Firma a base para o `DpsValidator` (Fase 5).

- [ ] **Step 1: Obter um XML de exemplo oficial**

Pegar um exemplo de DPS do MOC/pacote oficial e salvar em `src/test/resources/fixtures/dps-exemplo-oficial.xml`.

- [ ] **Step 2: Escrever o teste de validação**

`SchemaSanityTest.java`:

```java
package br.com.lc.nfse;

import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SchemaSanityTest {

    @Test
    void exemploOficialValidaContraXsd() {
        // NOME_DO_XSD_RAIZ vem de DECISOES.md (Task 1.1, Step 3)
        var xsd = getClass().getResourceAsStream("/schemas/DPS_v1.00.xsd");
        var xml = getClass().getResourceAsStream("/fixtures/dps-exemplo-oficial.xml");
        assertDoesNotThrow(() -> {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(xsd));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xml));
        });
    }
}
```

> Nota: o nome `DPS_v1.00.xsd` acima é ilustrativo — usar o nome real do XSD raiz registrado em `DECISOES.md`. Se o XSD raiz importar auxiliares por caminho relativo, eles resolvem por estarem na mesma pasta `schemas/`.

- [ ] **Step 3: Rodar e ver passar**

```powershell
.\mvnw.cmd -q -Dtest=SchemaSanityTest test
```

Expected: PASS. Se falhar por `import`/`include` não resolvido, ajustar a resolução de recursos (ex.: `LSResourceResolver` apontando para `schemas/`) — anotar o ajuste em `DECISOES.md`.

- [ ] **Step 4: Commit**

```powershell
git add .
git commit -m "test: sample oficial de DPS valida contra o XSD (smoke de schema)"
```

---

## FASES 2–8 — Roteiro (detalhar em passos exatos após a Fase 1)

Cada fase abaixo tem escopo, arquivos e estratégia de teste definidos. Os **passos bite-sized com código exato** serão escritos quando a Fase 1 entregar o XSD real e o `DECISOES.md`.

### Fase 2 — Modelo JAXB gerado do XSD
- **Files:** `pom.xml` (plugin de geração JAXB Jakarta, ex.: `org.jvnet.jaxb:jaxb-maven-plugin`), saída em `target/generated-sources`.
- **Responsabilidade:** transformar o XSD da DPS em classes Java, tornando o schema a fonte da verdade dos campos.
- **Teste:** um teste que instancia a classe raiz gerada, faz *marshal* para XML e confirma que o XML sai bem-formado. Registrar em `DECISOES.md` o nome da classe raiz e os campos obrigatórios (alimenta as Fases 6 e 7).

### Fase 3 — `DpsPackager` (gzip + base64)
- **Files:** `core/dps/DpsPackager.java`, teste `DpsPackagerTest.java`.
- **Responsabilidade:** função pura `String empacotar(String xml)` → gzip + base64; e `String desempacotar(String b64)` para o round-trip do teste.
- **Teste (já totalmente especificável):** `desempacotar(empacotar(x)).equals(x)` para um XML de exemplo; e que a saída é base64 válido. Fase independente do schema — pode ser adiantada.

### Fase 4 — `CertificadoLoader` + certificado de teste
- **Files:** `core/cert/CertificadoLoader.java`, `src/test/resources/certs/teste.p12` (autoassinado, gerado por `keytool`), teste `CertificadoLoaderTest.java`.
- **Responsabilidade:** carregar `.p12`/`.pfx` via `KeyStore PKCS12` e expor `PrivateKey` + `X509Certificate`.
- **Geração do cert de teste (passo do plano):** `keytool -genkeypair -alias teste -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore teste.p12 -storepass changeit -dname "CN=NFSE TESTE, OU=DEV, O=LC, C=BR" -validity 3650`.
- **Teste:** carrega `teste.p12`, assert que a chave privada e o certificado não são nulos.

### Fase 5 — `DpsValidator` (validação XSD)
- **Files:** `core/dps/DpsValidator.java`, teste `DpsValidatorTest.java`.
- **Responsabilidade:** `ResultadoValidacao validar(String xml)` contra o XSD oficial; erro do schema vira mensagem amigável (não exception crua).
- **Teste:** o sample oficial da Task 1.3 valida (ok=true); um XML com campo obrigatório removido falha com mensagem legível (ok=false).

### Fase 6 — `DpsSigner` (XMLDSig enveloped)
- **Files:** `core/dps/DpsSigner.java`, teste `DpsSignerTest.java`.
- **Responsabilidade:** assinar o elemento âncora (ex.: `infDPS`) com `java.xml.crypto` usando a chave de teste; assinatura `enveloped`. Algoritmo/C14N conforme `DECISOES.md`.
- **Teste:** assina um XML de exemplo e **verifica a própria assinatura** (round-trip de `XMLSignature.validate`). Correção estrutural do protótipo **não** depende de qual dos algoritmos padrão é usado — a conformidade ICP/gov só importa na transmissão (adiada).

### Fase 7 — `DpsBuilder` (DTO → DPS XML)
- **Files:** `core/dps/DpsBuilder.java`, `web/dto/RequisicaoDpsDto.java`, teste `DpsBuilderTest.java`.
- **Responsabilidade:** mapear o DTO de entrada mínimo (prestador, tomador, serviço, valores, `tpAmb=2`) para a classe raiz JAXB (Fase 2) e *marshal* para XML.
- **Teste (autoritativo):** a partir de um DTO de fixture, o XML gerado **valida contra o XSD** (reusa o `DpsValidator`). É a validação correta — não depende de eu transcrever campos à mão.

### Fase 8 — `DpsPreviewController` + tratamento de erros
- **Files:** `web/DpsPreviewController.java`, `web/GlobalExceptionHandler.java`, teste `DpsPreviewControllerTest.java` (`@WebMvcTest`).
- **Responsabilidade:** `POST /dps/preview` recebe o DTO, orquestra Builder → Validator → Signer → Packager, retorna `{ xmlAssinado, valido, pacoteGzipB64 }`. Validação falha → HTTP 422 com mensagem; falha de cert/assinatura → HTTP 500.
- **Teste:** DTO válido → 200 com corpo esperado; DTO inválido → 422 com mensagem legível.

---

## Self-Review (Fases 0–1)

- **Cobertura do spec:** ambiente (spec §6) → Fase 0; schema/assinatura/endpoint em aberto (spec §15) → Fase 1; pipeline (spec §7) → roteiro Fases 3–8; validação XSD (spec §12) → Fase 5 + smoke na Task 1.3. ✔
- **Placeholders:** Fases 0–1 têm comandos e código reais. Nomes ilustrativos (ex.: `DPS_v1.00.xsd`) estão marcados como "usar o nome real de DECISOES.md" — resolvidos dentro da própria Fase 1, não são TODOs pendentes. ✔
- **Consistência de tipos:** `nfse.ambiente` (int) usado igual no teste e nos YAMLs; caminho `schemas/` consistente entre Task 1.1 e 1.3. ✔

## Execution Handoff

Fases 0–1 prontas para executar. Após concluí-las (schema real em mãos), reabrir este plano para detalhar as Fases 2–8 em passos bite-sized.
