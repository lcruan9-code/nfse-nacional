# NFS-e Municipal (legado / fora do ADN) — Adaptador ABRASF 2.x

**Data:** 2026-08-14
**Status:** Design aprovado, pronto para plano de implementação
**Subsistema:** #5 da decomposição original (legado das prefeituras)
**Precede:** plano de implementação (writing-plans)

## 1. Contexto e motivação

O Padrão Nacional (ADN) — subsistemas #1/#2 já no ar — cobre apenas os municípios
aderentes e ativos (~1.900 hoje). As demais ~3.500 cidades emitem NFS-e pelos
**sistemas municipais legados** das prefeituras. Enquanto durar a transição da Reforma
Tributária (janela de ~1-3 anos até a unificação no ADN), existe um mercado-ponte real:
quem emitir "em qualquer cidade, ADN ou não, pela mesma casa" ganha o cliente.

**Driver escolhido:** cobertura comercial máxima. Por isso o primeiro adaptador é o
**ABRASF 2.x canônico** — o quase-padrão que a maioria dos provedores segue (Betha, IPM,
ISSNet, Ginfes, WebISS, DSF, Fiorilli, GissOnline, etc.), maximizando cidades destravadas
por unidade de esforço.

**Não reinventar a roda.** O ecossistema brasileiro já resolveu ABRASF. Este design é
deliberadamente um **porte + colheita** de fontes provadas (ver §7), não construção do zero.

## 2. Escopo

### Nesta fatia (primeira entrega)
- Rota/produto **separado** do ADN: `POST /v1/nfse-municipal` (o cliente escolhe o canal).
- **Resolvedor por IBGE**: dado o código IBGE, saber o provedor/versão/URLs (semeado a partir
  do registro do ACBr — ver §7).
- **Adaptador ABRASF 2.04** (base canônica), operação **`GerarNfse` síncrona**.
- **Modo laboratório** (mesma disciplina do núcleo #1 do ADN): monta → valida no XSD oficial
  da ABRASF → assina (XMLDSig) → monta envelope SOAP → **simula** a resposta da prefeitura.
- Persistência própria (`emissoes_municipais`), escopada por tenant.

### Fora desta fatia (evolução planejada, não agora)
- Transmissão real a uma homologação de prefeitura (exige A1 ICP + credencial; sandboxes
  mapeados em §7 para a fatia futura).
- Provedores/dialetos específicos além da base 2.04 (Betha, IPM, Ginfes 1.x…): entram como
  variações finas depois, um de cada vez.
- Operações `Consultar` e `Cancelar` (a interface já as prevê, mas não são implementadas agora).
- Versões ABRASF 1.x e 2.02/2.03 (o builder é preparado para ramificar por versão, mas só a
  2.04 é implementada nesta fatia).

### Restrições herdadas do projeto
- Build no **JDK 17** (nunca mexer no `JAVA_HOME` global, usado pelos migradores Java 8).
- Nada do caminho ADN (`core/dps`, `api/emissao`) é alterado — isolamento total.
- Git local; sem push sem pedido explícito. Sem segredos no repo.

## 3. Arquitetura

Espelha o split existente: `core/*` = motor offline testável; `api/*` = camada web/persistência.
Pacote novo, isolado, sob `br.com.lc.nfse`:

```
core/municipal/                     ← SPI + modelo canônico (neutro de dialeto)
  ProvedorMunicipal (interface)     ← o Strategy
  FabricaProvedor                   ← o Factory (tipo -> implementação)
  TipoProvedor (enum)               ← ADN, ABRASF_2X, GINFES, ISSNET, ... (só ABRASF_2X ativo)
  RpsRequest                        ← modelo canônico interno da nota
  ProvedorConfig                    ← endpoint, versão, estilo envelope, algoritmo assinatura, quirks
  ResultadoEmissao                  ← status, numeroNfse, codVerificacao, protocolo, xmls, mensagens[]
core/municipal/abrasf/              ← 1ª implementação concreta
  AbrasfProvedor                    ← implements ProvedorMunicipal; orquestra o pipeline
  AbrasfXmlBuilder                  ← RpsRequest -> GerarNfseEnvio (ramifica por versaoAbrasf)
  AbrasfValidator                   ← valida o XML no XSD ABRASF (via core/xsd/SanitizadorAncoras)
  AbrasfSigner                      ← XMLDSig enveloped, RSA-SHA1/SHA256 por config, C14N inclusiva
  AbrasfSoapEnvelope                ← monta o envelope SOAP (estilo por config)
  SimuladorAbrasf                   ← MODO LAB: GerarNfseResposta fictícia (feliz + rejeição)
  AbrasfRetornoParser               ← GerarNfseResposta -> ResultadoEmissao
core/municipal/registro/            ← o cérebro de roteamento
  ResolvedorProvedor                ← IBGE -> ProvedorRef (via tabela provedores_municipais)
core/xsd/                           ← utilitário compartilhado (extraído do DpsValidator)
  SanitizadorAncoras                ← remove âncoras ^$ dos xs:pattern (reuso ADN + municipal)
api/municipal/                      ← camada web
  MunicipalController               ← POST /v1/nfse-municipal ; GET /v1/nfse-municipal/{id}
  EmissaoMunicipalService           ← orquestra: resolve -> fabrica -> emite -> persiste
  EmissaoMunicipal (entity)         ← tabela emissoes_municipais
  dto/EmitirNfseMunicipalRequest, EmissaoMunicipalResponse
```

**Reúso deliberado (melhoria pontual justificada):** o sanitizador de âncoras `^$` hoje vive
dentro de `core/dps/DpsValidator`. Como o validador ABRASF precisa da mesma lógica, extraímos
para `core/xsd/SanitizadorAncoras` e o `DpsValidator` passa a chamá-lo. É refactor mínimo e a
serviço da fatia — não mexemos em mais nada do ADN.

## 4. Fluxo de dados

```
Cliente
  → POST /v1/nfse-municipal  { empresaId, tomador, servico, valores, codigoMunicipioIbge, ... }
  → MunicipalController         (Spring Security: X-Api-Key + escopo de tenant, igual ao /v1/nfse)
  → EmissaoMunicipalService
      → ResolvedorProvedor.resolver(ibge)
           ├─ tipo=ADN          → 409  "cidade é Padrão Nacional, use POST /v1/nfse"
           ├─ ausente           → 422  "município ainda não suportado" (+ registra o IBGE pedido)
           └─ tipo=ABRASF_2X    → ProvedorRef{ versao, urlHomolog, urlProd, estiloEnvelope, algoritmo }
      → FabricaProvedor.criar(ref) → AbrasfProvedor
      → AbrasfProvedor.emitir(rps, empresa, config):
           [1] AbrasfXmlBuilder.montar      → GerarNfseEnvio (por versaoAbrasf; 2.04 nesta fatia)
           [2] AbrasfValidator.validar      → XSD oficial ABRASF (via SanitizadorAncoras)  ← critério de sucesso
           [3] AbrasfSigner.assinar         → XMLDSig sobre InfDeclaracaoPrestacaoServico (+ Lote)
           [4] AbrasfSoapEnvelope.montar    → envelope da operação GerarNfse
           [5] SimuladorAbrasf.responder    → GerarNfseResposta fictícia (MODO LAB)
           [6] AbrasfRetornoParser.parse    → ResultadoEmissao
      → persiste EmissaoMunicipal (tenant, ibge, provedor, xmlEnviado, xmlRetorno, numeroNfse, status)
  → 202  { id, status, numeroNfse, codigoVerificacao, xml }
```

Chave de produção (`sk_live_`) → **501 Not Implemented** (não há transmissão real ainda),
espelhando o comportamento já adotado no caminho ADN.

## 5. Componentes — contratos

### `ProvedorMunicipal` (Strategy)
```java
public interface ProvedorMunicipal {
    TipoProvedor tipo();
    ResultadoEmissao emitir(RpsRequest rps, EmpresaFiscal emp, ProvedorConfig cfg);
    // evolução (fatias futuras): ResultadoConsulta consultar(...); ResultadoCancel cancelar(...);
}
```
- **O quê:** contrato único de emissão municipal, independente de dialeto.
- **Como usar:** obtido via `FabricaProvedor.criar(ref)`.
- **Depende de:** modelo canônico (`RpsRequest`, `ProvedorConfig`, `ResultadoEmissao`) — nada de web/JPA.

### `RpsRequest` (modelo canônico)
Dados neutros da nota (tomador, serviço, valores, IBGE, competência). **Não é ABRASF nem ADN.**
Cada provedor traduz deste modelo para o seu dialeto — é o que impede o ABRASF de vazar para o
contrato público da API e o que torna barato adicionar o próximo provedor.

### `ProvedorConfig`
`{ versaoAbrasf, urlHomolog, urlProd, estiloEnvelope, algoritmoAssinatura, quirks[] }`.
Vem do `ResolvedorProvedor`. É o que carrega a variabilidade por provedor/município **em dado**,
não em código.

### `ResolvedorProvedor`
- **O quê:** dado um código IBGE, devolve `ProvedorRef` (tipo + config) ou sinaliza ADN/ausente.
- **Fonte de dados:** tabela `provedores_municipais`, **semeada a partir do registro do ACBr** (§7),
  não digitada à mão.
- **Depende de:** apenas o repositório da tabela.

### `AbrasfProvedor` + auxiliares
Portados de `ACBr.Net.NFSe` (C#, MIT) — ver §7. `AbrasfXmlBuilder` ramifica por `versaoAbrasf`
espelhando as subclasses `ProviderABRASF20x` do ACBr; nesta fatia só a **2.04** é implementada.

## 6. Tratamento de erros

Camadas explícitas, cada falha com resposta clara e sem vazar detalhe sensível:

| Camada | Falha | Resposta |
|--------|-------|----------|
| Resolução | IBGE é ADN | 409 + orientação para `/v1/nfse` |
| Resolução | IBGE ausente no registro | 422 "município não suportado" + registra o IBGE pedido (demanda) |
| Montagem/validação | XML reprovado no XSD ABRASF | 422 com as mensagens do validador (nunca envia inválido) |
| Assinatura | cert/algoritmo inválido | 500 logado no servidor, sem vazar detalhe do cert |
| Transmissão (lab) | rejeição simulada | 200 com `status=rejeitada` + `ListaMensagemRetorno` (exercita o parse de erro real) |

**Isolamento:** qualquer exceção do mundo municipal fica contida no pacote `municipal/`;
o caminho ADN nunca é afetado.

**Assinatura SHA-1 e `secureValidation` do Java (pegadinha confirmada na pesquisa):** ABRASF
clássico exige RSA-SHA1 + digest SHA1 + C14N inclusiva + transform enveloped, assinando em dois
níveis (`InfDeclaracaoPrestacaoServico` e o `LoteRps`). O JDK bloqueia SHA1 no XMLDSig por
`org.jcp.xml.dsig.secureValidation`. O `AbrasfSigner` **desliga o secureValidation apenas quando
o algoritmo da config é SHA1**, mantendo validação segura + SHA256 nos provedores modernos. O
algoritmo é campo da `ProvedorConfig` (default SHA1), nunca assumido.

## 7. Reúso — fontes verificadas (o "de onde portar")

Pesquisa concluída (gh CLI + web + download dos XSDs). Licenças conferidas.

1. **Porte de montagem + assinatura:** [`ACBrNet/ACBr.Net.NFSe`](https://github.com/ACBrNet/ACBr.Net.NFSe)
   (**C#, licença MIT** — reúso comercial livre). Classes `Providers/ProviderABRASF.cs` +
   `ProviderABRASF200..204.cs` + `XmlSigning`. C#→Java ~1:1 (JAXB no lugar do DataContract; nosso
   XMLDSig no lugar do `XmlSigning`).
2. **XSDs oficiais:** `abrasf.org.br/biblioteca/arquivos-publicos/nfs-e`. Um **XSD monolítico por
   versão** (`schema nfse v2-04.xsd`, ~60 KB, contém `GerarNfseEnvio`/`GerarNfseResposta` inline).
   Download 2.04: `https://abrasf.org.br/biblioteca/arquivos-publicos/schema-nfse-v2-04/download`.
   Guardar em `resources/schemas/abrasf/`.
3. **Registro por IBGE (semear o resolvedor):**
   [`frones/ACBr`](https://github.com/frones/ACBr) → `Fontes/ACBrDFe/ACBrNFSeX/ACBrNFSeXServicos.ini`
   (38.439 linhas, **5.571 cidades por IBGE** + URLs prod/homolog) cruzado com
   `Provedores-Implementados.txt` (provedor → versão ABRASF). **Dados são fatos, não copyrightáveis**
   (o código Pascal é LGPL — usamos só os dados). Parsear o INI → seed da tabela `provedores_municipais`,
   registrando também `algoritmo` (SHA1 default) e endpoint de homologação.
4. **Sandboxes de homologação (fatia futura de transmissão real):** Ginfes
   (`homologacao.ginfes.com.br/ServiceGinfesImpl?wsdl`, ABRASF 1.x), Abaco (`enfs-hom.abaco.com.br`),
   BHISS teste, Fiorilli IssWeb.

**Não usar como base de código:** `flexait/nfse` e outros repos Java **sem licença** (todos os
direitos reservados); NFePHP/nfselib (licença GPL/AGPL provável) — só referência de fluxo.

## 8. Persistência

Migration **V5** — `provedores_municipais` (o registro): `codigo_ibge PK`, `nome`, `uf`, `tipo`,
`versao_abrasf`, `url_homolog`, `url_prod`, `estilo_envelope`, `algoritmo`, `quirks`. Semeada
via script de importação do INI do ACBr (parser à parte, executado uma vez; o SQL de seed entra
versionado).

Migration **V6** — `emissoes_municipais`: `id PK`, `conta_id`, `empresa_id`, `codigo_ibge`,
`provedor`, `numero_nfse`, `codigo_verificacao`, `protocolo`, `status`, `xml_enviado`,
`xml_retorno`, `criado_em`. Escopada por tenant (mesmo padrão de isolamento de `emissoes`).

Nenhuma tabela existente é alterada.

## 9. Testes (critério de "pronto")

Mesma disciplina do núcleo #1 do ADN. O teste-âncora define "pronto".

1. **Unit — teste-âncora:** `AbrasfXmlBuilder` monta o `GerarNfseEnvio` e ele **valida contra o
   XSD oficial da ABRASF 2.04**. (Equivalente ao teste DPS×XSD do ADN.)
2. **Unit:** `AbrasfSigner` — assinatura confere (referência ao `Id`, digest, algoritmo por config;
   caminho SHA1 com secureValidation desligado, caminho SHA256 com validação segura).
3. **Unit:** `ResolvedorProvedor` — IBGE ABRASF resolve com config correta; IBGE ADN deflete (409);
   IBGE desconhecido → 422.
4. **Integração:** `POST /v1/nfse-municipal` ponta-a-ponta (tenant escopado, IBGE ABRASF) → 202 com
   número simulado + XML assinado; cross-tenant → 404; chave `sk_live_` → 501.
5. **Reúso da infra de teste** existente: Testcontainers singleton + limpeza por `@BeforeEach`.

Cobertura-alvo: ≥ 80% no pacote `municipal/`.

## 10. Riscos e decisões conscientes

- **Prazo de validade (Reforma):** o legado municipal será aposentado pelo ADN. Investimento
  justificado só pela **janela de transição**. Mitigação: arquitetura plugável barata de evoluir
  e de descontinuar; nada do ADN acoplado.
- **Variação por município mesmo dentro do ABRASF** (alerta explícito do ACBr): por isso a config
  é por IBGE, não só por "modelo". A base 2.04 cobre o caso comum; quirks entram em dado.
- **Modo laboratório não prova transmissão real:** o critério de sucesso é o XSD, não uma NFS-e
  autorizada de verdade. Honesto e explícito — a transmissão real é fatia futura, dependente de
  A1 ICP + credencial de homologação.
- **Licença:** porte só do ACBr.Net (MIT) e dos dados do ACBr Pascal (fatos). Código LGPL/GPL/AGPL
  e repos sem licença ficam como referência de leitura, não de cópia.
```
