# Design — Núcleo de Emissão NFS-e Padrão Nacional (Protótipo #1)

- **Data:** 2026-08-13
- **Autor:** Ruan (LC Sistemas)
- **Status:** Aprovado para virar plano de implementação
- **Fontes de origem:** `implantação.txt`, `RECOMENDAÇÕES.txt` (specs genéricas de uma plataforma SaaS de NFS-e)

---

## 1. Contexto e intenção

A ambição de longo prazo é um **produto SaaS / API comercial de NFS-e** — uma API que outras
software houses contratam para emitir NFS-e sem lidar com a fragmentação fiscal do Brasil.

Mas a intenção imediata é **começar como protótipo**, provando a viabilidade técnica do
pedaço mais valioso e mais arriscado antes de investir nos subsistemas caros.

Este documento especifica **apenas o Protótipo #1**. Os demais subsistemas ficam registrados
na decomposição (seção 2) para dar direção, mas **não são escopo aqui**.

## 2. Decomposição do produto completo

Os `.txt` de origem descrevem, na prática, vários subsistemas independentes. Ordenados por
valor/risco:

| # | Subsistema | Quando |
|---|-----------|--------|
| **1** | **Emissão via Padrão Nacional (ADN)** — DPS → assina → empacota | **Agora (este spec)** |
| 2 | Camada API comercial própria (JSON padronizado + auth + persistência) | Depois que #1 emite de verdade |
| 3 | Assíncrono (fila + webhook) + resiliência (circuit breaker/backoff) | Quando houver volume/clientes |
| 4 | Cofre de certificados (AES-256-GCM / Vault) | Antes do 1º cliente externo real |
| 5 | Legado das prefeituras (SOAP, dialetos ABRASF, proprietários SP/RJ/Curitiba) | Só sob demanda, cidade a cidade |

O legado (#5) é onde a maioria dos projetos afunda. O Padrão Nacional (#1) é o oposto:
**um endpoint único para o Brasil inteiro**, REST/JSON, moderno — o lugar certo para provar viabilidade.

## 3. Reenquadramento do protótipo (sem certificado A1)

No momento **não há certificado A1 ICP-Brasil** disponível para testes. Como a transmissão real
ao ADN exige **mTLS com A1 válido** (a cadeia é validada contra a ICP-Brasil até na Produção
Restrita/homologação), a transmissão ponta-a-ponta fica **fora do protótipo**.

Isso, na verdade, melhora o foco: a transmissão (mTLS + POST) é a parte fácil e chata. A parte
**difícil e valiosa** — montar a DPS correta, assinar e empacotar no formato exato do ADN — é
100% comprovável **offline**.

Durante o desenvolvimento, a assinatura usa um **certificado autoassinado de teste** (a assinatura
XMLDSig fica estruturalmente válida; só não carrega a confiança ICP-Brasil, que só importa na
transmissão). Trocar pelo A1 real depois é uma mudança de configuração, não de código.

### Correção técnica relevante

As specs de origem afirmam que "a assinatura tag a tag do JSON é opcional". **No Padrão Nacional
isso é enganoso:** a **DPS é um XML que precisa ser assinado (XMLDSig com A1), compactado (gzip) e
enviado em base64**. O certificado não é uma fase tardia — é pré-requisito conceitual do núcleo.

## 4. Critério de sucesso do Protótipo #1

> Gerar uma **DPS assinada (XMLDSig)**, **válida contra o XSD oficial da Receita**, e **empacotada
> (gzip + base64)** exatamente no formato que o endpoint do ADN espera — **pronta para transmitir**
> no dia em que houver um A1.

Concretamente: um teste automatizado que parte de uma massa de dados fixa, gera a DPS, ela **passa**
na validação contra o XSD oficial, é assinada, a **assinatura verifica**, e o round-trip de
gzip/base64 fecha.

## 5. Arquitetura

- **Stack:** Spring Boot 3.x, Java 17 (build no JDK 17 já instalado — ver seção 6),
  build **Maven via Wrapper** (`mvnw`).
- **Duas camadas bem separadas:**
  - **`core`** — o coração fiscal, **sem dependência de web**. Beans simples, testáveis por JUnit
    puro. Recebe dados de entrada e produz a DPS assinada/validada/empacotada. É onde mora todo o
    valor e todo o risco técnico. Projetado para ser reaproveitado por #2 e #5 no futuro.
  - **`web`** — casca fina. Um controller REST que recebe JSON, chama o `core` e devolve o
    resultado. Serve para "ver rodando" e como base do produto comercial depois.

O `core` não sabe que o Spring existe.

## 6. Ambiente e pré-requisitos (verificado em 2026-08-13)

Verificação da máquina de desenvolvimento (Windows 11):

| Item | Status | Observação |
|------|--------|-----------|
| JDK 17 | ✅ presente | `C:\Program Files\Java\jdk-17` — Oracle 17.0.12 LTS, 64-bit. **Não é o padrão do sistema.** |
| JDK padrão do sistema | ⚠️ Java 8 | PATH e `JAVA_HOME` apontam para `jdk1.8.0_311` (32-bit), usado pelos migradores NetBeans/Ant. **Não mexer.** |
| Maven | ❌ ausente | Sem instalação global e sem `~/.m2`. |
| Gradle | ❌ ausente | Não usado. |
| winget | ✅ disponível | Caso se opte por instalar Maven global. |

**Decisões de ambiente:**

1. **Build com JDK 17 sem alterar o `JAVA_HOME` global.** O Java 8 padrão é usado pelos migradores
   Swing (NetBeans/Ant); mudar o global pode quebrá-los. O JDK 17 é apontado apenas no escopo do
   build/IDE deste projeto (`JAVA_HOME` por sessão ou "Project SDK" na IDE).
2. **Maven via Maven Wrapper (`mvnw`/`mvnw.cmd`).** O projeto é gerado já com o wrapper (Spring
   Initializr o inclui), então **não é preciso instalar Maven globalmente** — o wrapper baixa a
   distribuição correta na primeira execução (requer internet uma vez). Alternativa:
   `winget install Apache.Maven`.
3. **"Ambiente de testes" são duas coisas distintas:**
   - **Build/testes locais (JUnit):** onde o critério de sucesso (seção 4) é validado. Coberto por
     JDK 17 + wrapper. **Pronto para uso.**
   - **Homologação do ADN (Produção Restrita):** ambiente de transmissão real do governo. Continua
     **bloqueado pela falta de A1** (mTLS) e é escopo do passo futuro de transmissão, não deste protótipo.

## 7. Componentes do `core` (pipeline da DPS)

Quatro peças em cadeia, cada uma com responsabilidade única:

| Componente | Responsabilidade | Depende de |
|-----------|------------------|-----------|
| `DpsBuilder` | DTO de entrada → objeto DPS → XML | modelo JAXB gerado do **XSD oficial** do ADN |
| `DpsValidator` | valida o XML contra o XSD oficial; erro → mensagem amigável | XSDs em `resources/schemas/` |
| `DpsSigner` | assina o `infDPS` (XMLDSig *enveloped*) | `CertificadoLoader` |
| `DpsPackager` | gzip + base64 do XML assinado | — |

Peça de apoio:

| Componente | Responsabilidade |
|-----------|------------------|
| `CertificadoLoader` | carrega `.p12`/`.pfx` via `KeyStore PKCS12` (teste agora, A1 depois) |

**Decisão-chave:** gerar o modelo Java **a partir do XSD oficial** (JAXB/xjc). O schema do governo
vira a fonte da verdade dos campos obrigatórios, em vez de adivinharmos campo a campo.

## 8. Fluxo de dados

```
POST /dps/preview  (JSON: prestador, tomador, serviço, valores, tpAmb=2)
      │
      ▼
DpsBuilder ──► DpsValidator ──► DpsSigner ──► DpsPackager
   (XML)         (XSD ok?)      (assina)     (gzip+b64)
      │             │
      │             └─ inválido → HTTP 422 + erro legível
      ▼
HTTP 200 { xmlAssinado, valido:true, pacoteGzipB64 }  ← pronto para transmitir
```

A transmissão real (mTLS + POST no ADN) é um **passo cego reservado para o fim**, plugável quando
houver A1. O pacote que sai deste fluxo já é exatamente o que aquele passo enviará.

### Contrato de entrada (mínimo viável)

Um DTO enxuto, só o necessário para uma DPS válida (o XSD dita o resto):

- **Prestador:** CNPJ, inscrição municipal, código IBGE do município, regime tributário.
- **Tomador:** CNPJ/CPF, nome, endereço (conforme obrigatoriedade do schema).
- **Serviço:** código de tributação nacional, descrição, município de incidência do ISS.
- **Valores:** valor do serviço, alíquota ISS, indicador de ISS retido.
- **Ambiente:** `tpAmb = 2` (homologação) fixo neste protótipo.

Este contrato é **provisório** — o contrato comercial definitivo é escopo do #2.

## 9. Certificado

`CertificadoLoader` carrega um `.p12`/`.pfx` via `KeyStore` PKCS12. No protótipo aponta para um
**autoassinado de teste** em `src/test/resources/certs/` (claramente marcado como não-ICP; **nenhum
certificado real é commitado**). Trocar pelo A1 = mudar caminho + senha na configuração. O código de
assinatura não muda.

## 10. Configuração / profiles

`application.yml` com profiles `homolog` (URLs de Produção Restrita) e `prod` já estruturados. Mesmo
sem transmitir ainda, deixa o terreno pronto e honra a estratégia de isolamento de ambientes das
specs de origem.

## 11. Tratamento de erros

- **Validação XSD falha** → `HTTP 422` com mensagem legível (o "erro amigável instantâneo" elogiado
  nas specs de origem nasce daqui).
- **Falha ao carregar certificado** → `HTTP 500` com mensagem clara.
- **Falha na assinatura/empacotamento** → `HTTP 500` com contexto logado no servidor.

## 12. Estratégia de testes

- **JUnit 5.**
- **Golden test do pipeline:** fixture → gera DPS → **valida contra o XSD oficial (deve passar)** →
  assina → **verifica a assinatura** → round-trip gzip/base64. Passou = viabilidade provada.
- **WireMock:** reservado para o passo *futuro* de transmissão (não necessário agora).

## 13. Estrutura de projeto (proposta)

```
nfse-nacional/
  pom.xml
  src/main/java/br/com/lc/nfse/
    NfseApplication.java
    core/
      dps/{DpsBuilder,DpsValidator,DpsSigner,DpsPackager}.java
      model/            # JAXB gerado do XSD + DTOs de entrada
      cert/CertificadoLoader.java
    web/
      DpsPreviewController.java
      dto/RequisicaoDpsDto.java
  src/main/resources/
    schemas/            # XSDs oficiais do ADN
    application.yml     # profiles homolog/prod
  src/test/java/...     # JUnit (golden test)
  src/test/resources/
    certs/teste.p12     # autoassinado, SÓ teste
    fixtures/           # massa de dados de exemplo
```

## 14. Fora de escopo do Protótipo #1 (explícito)

Transmissão real (mTLS/POST), cancelamento, consulta, DANFSE/PDF, contrato/auth/persistência da API
comercial, filas/webhooks, Vault/criptografia em repouso, e **todo o legado das prefeituras**.

## 15. Perguntas em aberto (a confirmar na fonte oficial antes de codar)

A confirmar em `nfse.gov.br` / MOC da Receita na fase de plano (não serão chutadas):

1. **Versão atual do schema** da DPS e o pacote de XSDs correto.
2. **Algoritmo exato de assinatura** (RSA-SHA1 vs SHA-256) e o método de canonicalização (C14N) que
   o ADN exige, além de qual elemento recebe o `Id` e o `Reference`.
3. **Path exato do endpoint SEFIN** e o formato do corpo (para o passo futuro de transmissão).

## 16. Próximos passos após este spec

1. Ruan revisa este documento.
2. Skill `writing-plans` gera o plano de implementação detalhado, fase a fase.
3. Implementação incremental (passo a passo), com TDD no `core`.
