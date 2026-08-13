# nfse-nacional — Núcleo de Emissão NFS-e Padrão Nacional (Protótipo #1)

API/serviço que monta uma **DPS** (Declaração de Prestação de Serviços), valida contra o **XSD oficial
v1.01** da NFS-e Nacional, **assina** (XMLDSig) e **empacota** (gzip+base64) — o pacote pronto para
transmitir ao Ambiente de Dados Nacional (ADN) quando houver certificado A1.

> **Escopo do protótipo:** só o núcleo offline (montar → validar → assinar → empacotar). **Não**
> transmite ao governo (a transmissão exige mTLS com A1 ICP-Brasil, ainda indisponível). Assina com um
> certificado de teste autoassinado (não-ICP).

## Requisitos

- **JDK 17** (o projeto usa `C:\Program Files\Java\jdk-17`). **Não** usa o Java 8 padrão do sistema.
- Maven **não** precisa estar instalado — usa o **Maven Wrapper** (`mvnw.cmd`), que baixa o Maven na 1ª execução.

## Build e testes

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
```

Suíte cobre: round-trip do empacotamento, carga do certificado, carga/validação do schema, geração da
DPS válida no XSD, assinatura (assinar→verificar) e o endpoint completo.

## Rodar a API

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# opcional: assinar de verdade (senão devolve a DPS validada, não assinada)
$env:NFSE_CERTIFICADO_CAMINHO = "src/test/resources/certs/teste.p12"
$env:NFSE_CERTIFICADO_SENHA = "changeit"
.\mvnw.cmd spring-boot:run
```

### `POST /dps/preview`

Requisição (JSON):

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

Resposta (JSON): `{ valido, assinado, xml, pacoteGzipB64, mensagem }`.

- `422` quando a DPS não valida contra o XSD (com mensagem legível).
- `200` com `assinado=false` quando não há certificado configurado.

## Arquitetura

- **`core`** — coração fiscal, sem Spring: `DpsBuilder` → `DpsValidator` → `DpsSigner` → `DpsPackager`
  (+ `CertificadoLoader`). Testável por JUnit puro.
- **`web`** — casca fina: `DpsPreviewController` orquestra o pipeline; `CertificadoProvider` carrega o A1.

## Notas técnicas

- **Schema v1.01** (`src/main/resources/schemas/`), publicado 2026-02-09. Decisões oficiais em
  [`schemas/DECISOES.md`](src/main/resources/schemas/DECISOES.md).
- **Saneamento de âncoras `^$`:** os `<xs:pattern>` do XSD usam âncoras estilo .NET que o Xerces do
  Java rejeita; o `DpsValidator` remove essas âncoras antes de compilar o schema.
- **Assinatura:** XMLDSig enveloped, RSA-SHA256, âncora `infDPS`/`Id`. O algoritmo exato exigido pelo
  ADN deve ser confirmado no manual antes da transmissão real.

## Próximos passos

Transmissão real ao ADN (mTLS + A1); depois a camada de API comercial (#2): contrato JSON público,
autenticação, persistência, assíncrono e webhooks.
