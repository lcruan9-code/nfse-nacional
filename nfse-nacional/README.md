# nfse-nacional

Serviço de emissão de **NFS-e** em Java, com dois caminhos de emissão sob um mesmo contrato de API:
o **Padrão Nacional** (DPS → XSD → assinatura → pacote) e o **padrão municipal ABRASF 2.04**, com
roteamento automático por código IBGE do município.

> ### Status: MVP em desenvolvimento
>
> Este repositório é público como **portfólio de código**, não como produto pronto. O que já funciona
> e o que ainda não funciona está descrito abaixo, sem maquiagem:
>
> - **Não transmite ao governo.** A transmissão real ao Ambiente de Dados Nacional (ADN) exige mTLS
>   com certificado A1 ICP-Brasil, que o projeto ainda não tem. O ambiente de produção responde
>   `ProducaoIndisponivelException` de propósito.
> - **Assina com certificado autoassinado** (`CN=NFSE SANDBOX NAO-ICP`), versionado aqui só para os
>   testes rodarem. Não tem validade fiscal e não representa nenhuma empresa.
> - O caminho municipal usa um **simulador** no lugar do webservice da prefeitura.

## Stack

Java 17 · Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation) · PostgreSQL · Flyway ·
Hibernate · JUnit 5 · Testcontainers · Maven · Docker

## O que está implementado

**Núcleo fiscal (`core`, sem Spring)**

- **Padrão Nacional:** `DpsBuilder` monta a DPS → `DpsValidator` valida contra o XSD oficial v1.01 →
  `DpsSigner` assina em XMLDSig enveloped (RSA-SHA256, âncora `infDPS`/`Id`) → `DpsPackager`
  empacota em gzip + base64. Inclui os campos de **IBS/CBS** da reforma tributária.
- **Padrão municipal ABRASF 2.04:** construtor de XML, assinador, envelope SOAP, validador e parser
  de retorno, atrás de uma interface `ProvedorMunicipal` — trocar de provedor não toca o resto.
- **Roteamento por município:** `ResolvedorProvedor` descobre o provedor a partir do código IBGE,
  sobre um seed de ~3.079 municípios (mapa do ACBr). Município sem provedor conhecido devolve
  `NAO_SUPORTADO` em vez de falhar silenciosamente.

**API (`api`)**

- Multiempresa: `contas` → `empresas` → `api_keys`, com ambientes sandbox e produção separados.
- Autenticação por `X-Api-Key` via filtro do Spring Security. **A chave nunca é armazenada** — o
  banco guarda só o hash, com unicidade garantida no schema.
- Endpoints: `POST /v1/nfse`, `GET /v1/nfse/{id}`, `POST /v1/nfse-municipal`,
  `GET /v1/nfse-municipal/{id}`, `POST /admin/contas`, `/v1/empresas`, `/v1/whoami`, `/health`,
  `POST /dps/preview`.
- Erro sempre no mesmo envelope (`{ "erro": { "codigo", "mensagem" } }`), inclusive no 401.
- 8 migrations Flyway; `ddl-auto: validate` — o schema é do Flyway, o Hibernate só confere.

**Testes** — 26 classes, 61 testes. Cobrem o round-trip do empacotamento, carga de certificado,
carga e validação de schema, geração de DPS válida no XSD, assinar→verificar, o pipeline ABRASF
completo, autenticação por API key e os repositórios contra **PostgreSQL real via Testcontainers**.

## Rodar

Precisa de **JDK 17** (o projeto não compila no Java 8) e Docker para os testes de integração.
Maven não precisa estar instalado — o wrapper baixa.

```bash
./mvnw test
```

```bash
./mvnw spring-boot:run
```

Configuração por variável de ambiente (defaults de desenvolvimento em `application.yml`):

| Variável | Para quê |
|---|---|
| `NFSE_DB_PASSWORD` | senha do PostgreSQL |
| `NFSE_ADMIN_KEY` | chave do endpoint administrativo |
| `NFSE_CERT_CAMINHO` | caminho do `.p12` usado para assinar |
| `NFSE_CERT_SENHA` | senha do `.p12` |

Sobe também em container: o `Dockerfile` é multi-stage e limita o heap ao container
(`MaxRAMPercentage=70`), para caber em instância pequena.

### Exemplo — `POST /dps/preview`

```json
{
  "cnpjPrestador": "11222333000181",
  "codMunEmissor": "3550308",
  "serie": "1",
  "numero": "1",
  "dhEmiUtc": "2026-08-13T12:00:00-03:00",
  "dataCompetencia": "2026-08-13",
  "codMunPrestacao": "3550308",
  "codTribNacional": "010101",
  "descricaoServico": "Consultoria em TI",
  "valorServico": "1500.00",
  "opSimplesNacional": 1,
  "regimeEspecial": 0,
  "tributacaoIssqn": 1,
  "tipoRetencaoIssqn": 1
}
```

Resposta: `{ valido, assinado, xml, pacoteGzipB64, mensagem }`. Devolve **422** quando a DPS não
valida contra o XSD, com mensagem legível, e **200 com `assinado=false`** quando não há certificado
configurado.

## Duas decisões técnicas que valem a leitura

**Âncoras `^$` no XSD oficial.** Os `<xs:pattern>` dos schemas da NFS-e usam âncoras no estilo .NET.
O Xerces do Java as rejeita e o schema simplesmente não compila. O `SanitizadorAncoras` remove essas
âncoras antes de compilar — sem isso, nenhum validador Java lê os XSDs oficiais.

**Núcleo fiscal sem Spring.** Todo o `core` é Java puro, testável com JUnit sem subir contexto. O
Spring fica na casca (`api`, `web`). Isso mantém a suíte do núcleo em milissegundos e deixa a regra
fiscal independente do framework.

## Próximos passos

Transmissão real ao ADN (mTLS + A1 ICP-Brasil), emissão assíncrona com webhooks, e substituir o
simulador municipal pelos webservices reais das prefeituras.
