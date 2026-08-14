# lc_nfse.jar — Tela de teste de NFS-e — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Tela Swing (padrão LC) que lê a empresa do MySQL, chama a nossa API sandbox e imprime um DANFSE fictício em PDF.

**Architecture:** App desktop standalone. Camadas: `Config` → `EmpresaDao` (MySQL) → `NfseApiClient` (HttpClient → nossa API) → `DanfsePdf` (iText) → `NfseTela` (Swing, componentes LC) → `Main`. Compila contra os jars de `lib/` do install LC.

**Tech Stack:** Java 17, Swing + componentes LC (`CampoTexto`, `CampoValorNumerico`), mysql-connector 5.1.36, iText 2.1.7, HttpClient nativo.

## Global Constraints

- **JDK 17** (`C:\Program Files\Java\jdk-17`). **PI.jar NÃO é tocado.** Novo jar: `lc_nfse.jar`.
- Libs do LC em `C:\LC sistemas - Softhouse - 321\lib\` (referenciadas no classpath, não copiadas).
- MySQL **só leitura** (SELECT) no `lc_relatorio`. Nunca escrever no banco do ERP.
- Chave sandbox + `empresaId` **fixos** no `lc_nfse.properties` (pré-criados na Fase 0).
- Projeto em `C:\PROJETOS\Ruan\NFS-e\lc_nfse\`. **GUI não é testável automaticamente** — validação visual é do Ruan; aqui garantimos build + smokes headless.
- A API (Spring Boot + Postgres) precisa estar no ar para as Fases 0 e 2.

---

## FASE 0 — Setup do tenant na nossa API (chave + empresaId fixos)

- [ ] **Step 1: Subir a API** (`nfse-postgres` no ar): `spring-boot:run` em background.
- [ ] **Step 2: IBGE da empresa 1:** `SELECT codigocidade FROM cidades WHERE id=172`.
- [ ] **Step 3: Criar Conta:** `POST /admin/contas` (X-Admin-Key `admin_dev_key`) → guardar `chaveSandbox`.
- [ ] **Step 4: Registrar Empresa** na API: `POST /v1/empresas` (X-Api-Key=chaveSandbox) com
  `{cnpj (14 díg., sem máscara), razaoSocial, inscricaoMunicipal (im), codMunIbge (IBGE da cidade 172),
  opSimplesNacional:"ME_EPP", regimeEspecialTributacao:"NENHUM"}` → guardar `empresaId`.
- [ ] **Step 5:** Gravar `chaveSandbox` e `empresaId` no `lc_nfse/lc_nfse.properties`. Parar a API.

## FASE 1 — Projeto, Config, EmpresaDao

**Files:** `lc_nfse/lc_nfse.properties`, `lc_nfse/src/br/com/lc/nfse/tela/Config.java`,
`EmpresaInfo.java`, `EmpresaDao.java`; smoke `SmokeDao.java`.

- `Config`: lê o `.properties` (mysql url/user/pass, api.baseUrl, api.key, api.empresaId).
- `EmpresaInfo`: record (cnpj, razaoSocial, fantasia, im, crt, regime, ibge, cidade).
- `EmpresaDao.buscar(id)`: JDBC (mysql-connector), SELECT com join `cidades`. Só leitura.
- **Smoke:** `SmokeDao` (main) imprime a empresa 1. Rodar com classpath do mysql-connector.

## FASE 2 — NfseApiClient

**Files:** `NfseApiClient.java`, DTOs (`EmitirResult`); smoke `SmokeApi.java`.

- `emitir(descricao, codTrib, codMunPrest, valor, tribIssqn, tipoRet, simular)` → `POST /v1/nfse`
  (HttpClient, header X-Api-Key, corpo JSON montado à mão) → parse `{id,status}`.
- `consultar(id)` → `GET /v1/nfse/{id}` → `{status, chaveAcesso, numeroNfse, xmlDps, motivo}`.
- `testarConexao()` → GET `/v1/whoami` sem chave → 401 = no ar.
- **Smoke:** `SmokeApi` (com a API no ar) emite e consulta, imprime a chave de acesso.

## FASE 3 — DanfsePdf (iText)

**Files:** `DanfsePdf.java`; smoke `SmokePdf.java`.

- `gerar(EmpresaInfo prest, tomadorNome, tomadorDoc, descricao, valor, numero, chaveAcesso, arquivoPdf)`
  → PDF 1 página (iText 2.1.7): cabeçalho "NFS-e (SANDBOX)", prestador, tomador, serviço, valor, número,
  chave, marca d'água "SANDBOX — SEM VALOR FISCAL".
- **Smoke:** `SmokePdf` gera um PDF de exemplo e confirma que o arquivo existe.

## FASE 4 — NfseTela (UI) + Main + build

**Files:** `NfseTela.java`, `Main.java`, `build.bat`, `run.bat`.

- `NfseTela` (`JFrame`): header com empresa; painel Prestador (read-only); painel Serviço
  (`CampoTexto` descrição/codTrib, `CampoValorNumerico` valor, combo simular); painel Tomador
  (`CampoTexto`); barra com [Testar conexão] [Emitir] [Imprimir]; status bar. Ao Emitir: chama o client,
  mostra status/chave; habilita Imprimir → chama `DanfsePdf` e abre o PDF (`Desktop.open`).
- `Main`: carrega Config, busca empresa (DAO), cria e mostra a `NfseTela`.
- `build.bat`: `javac` de tudo com `-cp` das libs do LC → `jar cfe lc_nfse.jar ...`.
- `run.bat`: `java -cp "lc_nfse.jar;<libs LC>" br.com.lc.nfse.tela.Main`.
- **Verificação:** build compila; `run.bat` documentado pro Ruan rodar e ver a tela.

## Self-Review

- Cobertura: empresa MySQL (spec §3) → Fase 1; API (§4) → Fase 2/0; form (§5) → Fase 4; PDF (§6) → Fase 3;
  build/componentes (§2,§7) → Fase 4. ✔
- GUI não-testável documentado; smokes headless cobrem DAO/API/PDF. ✔

## Execution Handoff

Requer API + Postgres no ar (Fases 0 e 2). Executar em ordem.
