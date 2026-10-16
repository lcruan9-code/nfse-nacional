# Projeto: Plataforma NFS-e da LC Sistemas — Contexto Completo

> **Para quem lê (humano ou IA):** este documento é o contexto integral do projeto de emissão de
> Nota Fiscal de Serviço eletrônica (NFS-e) que estamos construindo na LC Sistemas. Ele foi escrito
> para ser autoexplicativo — se você é um assistente de IA recebendo isto, tem aqui tudo que precisa
> para entender o estado atual, as decisões tomadas e os próximos passos, sem precisar do histórico da
> conversa original. Data de referência: **15/08/2026**.

---

## 1. Resumo em uma página

Estamos construindo uma **API própria para emitir NFS-e** (a nota fiscal de quem presta serviço).
No Brasil isso é um caos: existe um novo **Padrão Nacional** (o "ADN", unificado pelo governo) e, ao
mesmo tempo, **milhares de sistemas municipais legados** diferentes (cada prefeitura com o seu). Nossa
plataforma cobre **os dois mundos pela mesma API**.

**Estado atual:** a plataforma está **construída, testada e publicada na nuvem**, funcionando em
**"modo laboratório"** — ela monta, valida e assina a nota corretamente, mas ainda **não transmite
notas reais** porque falta o **certificado digital A1** (ICP-Brasil) e a homologação junto à
prefeitura. A autorização hoje é *simulada*.

**O que falta para valer juridicamente:** basicamente o **A1** (em solicitação à LC para uma empresa
piloto em Mãe do Rio/PA) + homologação. A engenharia já está pronta para "plugar" o certificado.

**Por que vale a pena:** hoje quem emite NFS-e paga um agregador externo (eNotas, NFE.io, PlugNotas)
por nota ou por mensalidade. Uma solução própria **elimina esse custo** para os clientes da LC e vira
**diferencial do LC ERP**. Ressalva honesta: a Reforma Tributária vai unificar tudo no ADN nos
próximos anos, então o **ADN é o investimento durável** e o legado municipal é **mercado-ponte**.

---

## 2. Glossário (termos que aparecem o tempo todo)

| Termo | O que é |
|---|---|
| **NFS-e** | Nota Fiscal de Serviço eletrônica (a nota de quem presta serviço; competência **municipal**). |
| **ADN / Padrão Nacional** | *Ambiente de Dados Nacional* — o sistema único de NFS-e do governo federal, criado para substituir os municipais. |
| **DPS** | *Declaração de Prestação de Serviço* — o XML que a gente monta e assina para gerar a NFS-e no Padrão Nacional. |
| **ABRASF** | Padrão técnico (da associação das secretarias de finanças) que a **maioria dos sistemas municipais** segue, em versões 1.x e 2.x. |
| **Provedor** | A empresa/tecnologia que roda a NFS-e de uma prefeitura (ex.: Betha, IPM, Ginfes, ISSIntel, Fiorilli). |
| **Código IBGE** | O código de 7 dígitos que identifica cada município — usamos como **chave de roteamento**. |
| **Certificado A1** | Certificado digital (ICP-Brasil) em arquivo. Serve para **assinar** a nota **e** para **conectar** nos webservices (eles exigem o certificado na conexão). |
| **mTLS** | Conexão TLS que exige certificado **do cliente** (não só do servidor). Os webservices municipais usam isso — por isso o A1 é obrigatório até para conectar. |
| **XSD** | O "esquema" oficial que define o formato exato do XML. Validamos toda nota contra o XSD oficial antes de assinar. |
| **XMLDSig** | Assinatura digital de XML (o jeito padrão de assinar notas fiscais). |
| **DANFSE** | O PDF "bonito" da nota, para o cliente ver/imprimir. |
| **IBS/CBS** | Os novos tributos da Reforma Tributária que a nota já precisa comportar. |

---

## 3. O produto e a estratégia

### 3.1. O problema
Emitir NFS-e é uma dor recorrente de qualquer empresa de serviços. Não há um padrão único: além do
novo ADN, cada prefeitura pode usar um provedor/layout diferente. Quem resolve isso "por baixo" hoje
são agregadores pagos.

### 3.2. Nossa proposta
Uma API única que decide, **pelo código IBGE da cidade**, se emite pelo **Padrão Nacional (ADN)** ou
pelo **sistema municipal** correspondente — de forma transparente para o cliente.

### 3.3. Onde está o valor (honesto)
- **Economia direta:** elimina a taxa por nota/mensalidade que a LC (ou seus clientes) pagaria a um
  agregador.
- **Diferencial de produto:** o LC ERP passa a "já emitir NFS-e nacional e municipal".
- **Ressalva — a Reforma:** o ADN vai aposentar os sistemas municipais nos próximos ~1–3 anos. Então
  o legado municipal é uma janela de transição (mercado-ponte), enquanto o ADN é a base duradoura.
- **Leitura de mercado:** como diferencial dentro do ecossistema LC, o retorno é bem provável. Como
  produto SaaS vendido a outras software houses, é possível, mas o mercado já tem players fortes
  (eNotas, NFE.io, PlugNotas) — é uma disputa maior.

---

## 4. O que já está construído (funciona ponta a ponta)

Tudo abaixo está **implementado, testado (61 testes automatizados) e publicado na nuvem**.

### 4.1. Núcleo do Padrão Nacional (ADN)
Monta a **DPS** → valida no **XSD oficial** → **assina** (XMLDSig) → **empacota** (gzip+base64,
formato que o governo espera). É o coração da NFS-e nacional.

### 4.2. API comercial
- **Autenticação por chave** (`X-Api-Key`, formatos `sk_test_…`/`sk_live_…`).
- **Multi-tenant:** estrutura `Conta → Empresas` (cada cliente isolado; um não vê o do outro).
- **Emissão sandbox** (`POST /v1/nfse`) que monta+valida+assina a DPS de verdade e **simula** a
  autorização (chave de acesso de 50 dígitos, número da nota).
- **Consulta** (`GET /v1/nfse/{id}`), **DANFSE em PDF**, e os campos de **IBS/CBS** da Reforma.

### 4.3. Emissão Municipal (ABRASF) — cidades fora do ADN
- **Rota separada** (`POST /v1/nfse-municipal`) para não misturar com o caminho nacional.
- **Roteamento por IBGE:** um "cérebro" (`ResolvedorProvedor`) que, dado o código da cidade, sabe se
  ela é ADN, se é um provedor municipal suportado, ou se ainda não é suportada.
- **Adaptador ABRASF 2.04** completo: monta o XML → valida no XSD oficial da ABRASF → assina
  (XMLDSig RSA-SHA1, como o padrão clássico exige) → monta o envelope SOAP → **simula** a resposta da
  prefeitura → interpreta o retorno.
- **Arquitetura plugável** (padrão *Strategy + Factory*): adicionar um novo provedor (Betha, IPM…) é
  criar uma classe nova, sem tocar no resto.

### 4.4. Mapa completo de municípios
Importamos o cadastro do **ACBr** (projeto open-source de referência no Brasil): **3.079 municípios**
indexados por IBGE, cada um com seu provedor, versão e endereços de webservice.
- Classificação **honesta**: `ADN` (8 cidades marcadas como Padrão Nacional) e `NAO_SUPORTADO`
  (3.071) — **nenhuma** cidade marcada como "emitível" sem termos validado o provedor de verdade. Ou
  seja, o mapa dá **conhecimento e endereços prontos**, não uma falsa capacidade de emissão.

### 4.5. Infraestrutura
- Publicada em **https://nfse-nacional.onrender.com** (Render, via Docker).
- Banco **PostgreSQL** na **Neon**.
- **Deploy automático** a cada atualização; endpoint de saúde (`/health`).

---

## 5. O que é REAL vs. o que é SIMULADO (transparência total)

| Etapa | Estado |
|---|---|
| Montar a nota (XML) no formato oficial | ✅ real |
| Validar contra o XSD oficial | ✅ real |
| Assinar digitalmente (XMLDSig) | ✅ real (com certificado de **teste**, sem valor jurídico) |
| Empacotar / montar envelope SOAP | ✅ real |
| **Transmitir para o governo/prefeitura** | ❌ **simulado** (não enviamos de verdade ainda) |
| Autorização / número da nota | ❌ **simulado** |
| Emissão de nota com **valor jurídico** | ❌ **ainda não** — depende de A1 + homologação |

**Notas reais emitidas até agora: zero.** É um protótipo funcional completo, pronto para virar real.

---

## 6. O que falta para emitir de verdade

1. **Certificado A1 (ICP-Brasil)** — o item que destrava tudo. Ele é necessário para **duas** coisas:
   (a) **assinar** o conteúdo da nota e (b) **conectar** nos webservices das prefeituras, que exigem o
   certificado já na camada de conexão (**mTLS** — ver seção 7). *Em solicitação à LC para a empresa
   piloto de Mãe do Rio/PA.*
2. **Ligar a transmissão real** — trocar a autorização simulada pelo envio efetivo. A montagem já
   está pronta; é "plugar" o certificado.
3. **Homologação por cidade/provedor** — todo ambiente (ADN ou prefeitura) exige um processo de
   aprovação (mandar notas de teste até liberar produção). Sem custo em dinheiro; custa tempo.
4. **Dois ajustes de robustez** (já mapeados, rápidos): numeração de nota à prova de concorrência e
   validação de entrada na API.

---

## 7. A cidade-piloto: Mãe do Rio/PA (e uma descoberta importante)

Escolhemos **Mãe do Rio/PA** como piloto. Investigamos o cadastro e o endpoint real:

- **IBGE:** 1504059 · **Provedor:** ISSIntel · **Padrão:** ABRASF **1.00** (nosso adaptador atual é
  2.04 → a versão 1.x é a próxima a construir).
- **Endpoint:** `https://maedorio-pa.issintegra.com.br/webservices/abrasf/api` (só produção).

**Descoberta (spike de conectividade real):** tentamos bater no endpoint da prefeitura **sem
certificado**, para testar a API contra a realidade. Resultado honesto:
- O endpoint da Mãe do Rio **recusa a conexão** de clientes HTTPS normais (Java, Python) —
  *handshake_failure*.
- Testamos um endpoint conhecido-aberto (Ginfes, homologação) com a mesma stack: ele respondeu
  **"400 — No required SSL certificate was sent"**.
- **Conclusão:** os webservices municipais exigem o **certificado A1 já na conexão (mTLS)**. Ou seja,
  **não existe teste ao vivo contra prefeitura real sem o A1** — nem Mãe do Rio, nem Ginfes. O A1 não
  é só para assinar; é a credencial que o servidor exige para aceitar a conversa.

**Implicação:** o adaptador ABRASF 1.x (ISSIntel) pode ser **construído e validado no XSD/laboratório**
agora (como fizemos com o 2.04), a partir da documentação; a transmissão real destrava quando o A1
chegar.

---

## 8. Custos

| Item | Custo | Observação |
|---|---|---|
| **Certificado A1** (ICP-Brasil) | ~R$ 120–250/ano | por CNPJ emitente |
| **Hospedagem** | R$ 0 hoje → ~R$ 130–550/mês | grátis no plano free (limitado); pago só na escala/produção séria |
| **Homologação por prefeitura** | R$ 0 | custa tempo de desenvolvimento, não dinheiro |
| **Ambiente Nacional (ADN)** | Grátis | mantido pelo governo |
| **Taxa por nota emitida** | R$ 0 | nós somos o agregador — não pagamos por-nota a terceiros |

**Resumo:** fora o A1, o único gasto recorrente real é **hospedagem**, e só quando for para produção.

---

## 9. Stack técnica e onde as coisas estão

- **Linguagem/Framework:** Java 17 + Spring Boot 4.1 (build via Maven wrapper `mvnw`).
- **Banco:** PostgreSQL 16; migrações via Flyway (V1→V8); testes de integração com Testcontainers.
- **Assinatura/validação:** `javax.xml.crypto` (XMLDSig), validação XSD (Xerces).
- **Repositório (código-fonte):** local em `C:\PROJETOS\Ruan\NFS-e\` (Git, branch `master`); cópia
  privada no GitHub em `github.com/lcruan9-code/nfse-nacional`.
- **Produção:** API em `https://nfse-nacional.onrender.com` (Render, Docker) + banco na Neon.
- **Testes:** 61 automatizados, todos verdes (unitários + integração ponta a ponta).

**Principais endpoints da API:**
- `POST /v1/nfse` — emissão Nacional (sandbox) · `GET /v1/nfse/{id}` — consulta
- `POST /v1/nfse-municipal` — emissão Municipal (ABRASF) · `GET /v1/nfse-municipal/{id}` — consulta
- `POST /admin/contas` — cria conta (uso interno) · `GET /v1/whoami` · `/v1/empresas` (CRUD)
- `GET /health` — saúde · `POST /dps/preview` — pré-visualização da DPS

**Como rodar localmente (resumo):**
1. Requer **JDK 17** e **Docker** (para os testes de integração).
2. Build/testes: `./mvnw test` (o projeto usa o JDK 17; a máquina tem Java 8 como padrão para outros
   sistemas, então o JDK 17 é apontado só por sessão — **nunca** mudar o `JAVA_HOME` global).
3. A API sobe com `server.port` configurável; em produção lê as credenciais de variáveis de ambiente.

---

## 10. Decisões técnicas e "pegadinhas" já resolvidas
*(Para o próximo desenvolvedor/IA não repetir o aprendizado do zero.)*

- **Reúso, não reinvenção:** a lógica de montagem/assinatura ABRASF foi **portada do `ACBr.Net.NFSe`**
  (C#, licença **MIT** — livre para uso comercial). Os XSDs vêm do site oficial da **ABRASF**. O mapa
  de cidades vem do **ACBr** (dados = fatos, não copyrightáveis; o código Pascal do ACBr é LGPL e
  **não** foi copiado).
- **Assinatura ABRASF é SHA-1:** o padrão municipal clássico exige RSA-SHA1 + digest SHA1, o oposto do
  ADN (SHA-256). O Java bloqueia SHA-1 por padrão — desligamos essa trava **apenas** no caminho SHA-1
  (configurável por provedor).
- **Âncoras `^$` nos XSDs:** os XSDs fiscais brasileiros usam sintaxe de regex do .NET que o Java
  rejeita; saneamos as âncoras antes de validar (utilitário compartilhado entre ADN e municipal).
- **Isolamento total:** o mundo municipal vive em pacotes separados (`core/municipal`, `api/municipal`)
  — o caminho ADN, que já estava no ar, nunca foi alterado.
- **Roteamento honesto:** a tabela sabe o provedor de cada cidade, mas "conhecer ≠ emitir" — só
  marcamos como emitível o que validarmos de verdade.
- **Ambiente:** Spring Boot 4 modularizou o Flyway; Testcontainers 1.21.4 é o que funciona com o
  Docker 29; o surefire ignora classes de teste terminadas em `*IT` (renomeamos para `*Test`); e o
  Java 17 não tem o *pattern matching* de `switch` do Java 21 (usamos `instanceof` encadeado).

---

## 11. Processo de trabalho (como o projeto foi construído)

O desenvolvimento seguiu um fluxo disciplinado, fatia por fatia: **brainstorm → especificação →
plano detalhado → implementação em TDD (teste primeiro) → revisão de código → commit**. Cada tarefa
foi implementada e revisada de forma isolada, com testes automatizados como critério de "pronto".
Toda a documentação de design e planos está versionada em `docs/superpowers/` no repositório.

---

## 12. Próximos passos (roadmap)

**Concluído**
- ✅ Núcleo Nacional (ADN) + API comercial (auth, multi-tenant, DANFSE, IBS/CBS).
- ✅ Adaptador Municipal ABRASF 2.04 + arquitetura plugável.
- ✅ Import do mapa completo (3.079 cidades por IBGE).

**Em curso / próximo**
- ⏳ **Cidade-piloto Mãe do Rio/PA:** construir o adaptador **ABRASF 1.x (ISSIntel)** a partir da
  documentação (validado em laboratório).
- ⏳ **Certificado A1** (solicitação à LC) → destrava a transmissão real.
- ⏳ **Primeira nota real:** A1 + homologação da cidade → uma nota autorizada de verdade
  (prova de conceito comercial).

**Depois**
- Novos provedores por demanda (Betha, IPM, Fiorilli…), priorizados pelas cidades onde a LC tem
  clientes.
- Foco no **ADN** como base durável da plataforma.
- Ajustes de robustez para produção (numeração concorrente, validação de entrada, hospedagem paga).

---

## 13. Para a IA que recebe este documento

Se o objetivo é **continuar ou avaliar** este projeto, os pontos de partida mais úteis são:
1. Entender que **existem dois caminhos** (ADN nacional e municipal ABRASF) e que o roteamento é por
   **código IBGE**.
2. O estado é **"laboratório"**: tudo funciona menos a **transmissão real**, bloqueada pela falta do
   **certificado A1** (necessário inclusive para conectar via mTLS).
3. A próxima peça técnica é o **adaptador ABRASF 1.x** para o provedor **ISSIntel** (Mãe do Rio),
   espelhando o adaptador 2.04 já existente.
4. Manter a disciplina de **honestidade** (não marcar como "emitível" o que não foi validado) e de
   **isolamento** (não tocar no caminho ADN ao mexer no municipal).
5. A estratégia de negócio prioriza o **ADN** (durável) sobre o legado municipal (transitório pela
   Reforma Tributária).

*Documento gerado em 15/08/2026 como contexto de handoff do projeto NFS-e da LC Sistemas.*
