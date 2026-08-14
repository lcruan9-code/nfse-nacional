# Design — lc_nfse.jar: Tela de teste de emissão NFS-e (cliente da API)

- **Data:** 2026-08-14
- **Autor:** Ruan (LC Sistemas)
- **Status:** Aprovado para virar plano
- **Contexto:** tela Swing de teste que consome a nossa API comercial de NFS-e (fatias #1/2a/2b),
  no padrão do LC ERP. Só para testes. **Não altera o PI.jar.**

## 1. Objetivo e sucesso

Uma tela desktop (Java Swing, padrão LC) que lê a **empresa real** do MySQL do ERP, deixa preencher
os dados **mínimos** de um serviço, chama a **nossa API sandbox** (`POST /v1/nfse` → `GET /v1/nfse/{id}`)
e imprime um **DANFSE fictício em PDF**. Tudo fictício (sandbox).

**Sucesso:** abrir `lc_nfse.jar` → ver os dados da empresa 1 → preencher descrição/valor → **Emitir** →
ver status AUTORIZADA + chave de acesso vindos da nossa API → **Imprimir** → abrir o PDF do DANFSE.

## 2. Decisões (aprovadas)

- **Componentes reais do LC** (`newpackage.CampoTexto`, `newpackage.CampoValorNumerico` — ambos
  `JTextField` com construtor vazio; `getValor():double`, `setNumeroDeCaracteres(int)`), de `lib/`.
- **Config com tudo pré-carregado** e **chave fixa**: a Conta+Empresa são criadas **uma vez** na nossa
  API (passo de setup) e a chave sandbox + `empresaId` ficam fixos no `lc_nfse.properties`. Sem
  provisionamento automático na tela (ajustamos depois dos testes).
- Tela = `JFrame` estilizado como as telas LC (que são `JDialog`).

## 3. Dados da empresa (MySQL, só leitura)

Fonte: `rede.txt` → `127.0.0.1:3306`, db `lc_relatorio`, `root/123456`, empresa padrão **id 1**.
`EmpresaDao` faz um SELECT com join:
```sql
SELECT e.cnpj, e.razao_social, e.fantasia, e.im, e.crt, e.regime_tributario,
       c.codigocidade AS ibge, c.nome AS cidade
FROM empresa e JOIN cidades c ON c.id = e.id_cidades
WHERE e.id = 1;
```
Empresa 1 (confirmado): `MARAJO TECH ...`, CNPJ `44.346.528/0001-92`, Simples Nacional (`crt=1`),
cidade id 172. **Só SELECT — nunca escreve no banco do ERP.**

## 4. Integração com a nossa API

`NfseApiClient` (HttpClient nativo do Java):
- `POST /v1/nfse` com `X-Api-Key` (fixa do config) e corpo
  `{ empresaId, servico{codTribNacional, descricao, codMunPrestacao}, valores{valorServico, tributacaoIssqn, tipoRetencaoIssqn}, simular }`
  → 202 `{ id, status }`.
- `GET /v1/nfse/{id}` → `{ status, chaveAcesso, numeroNfse, xmlDps, motivo }`.
- Botão "Testar conexão" (GET sem chave → 401 = no ar).

**Pré-requisito de runtime:** a API (Spring Boot + Postgres) precisa estar no ar em `http://localhost:8080`.

**Setup (feito uma vez na implementação):** com a API no ar, `POST /admin/contas` → chave sandbox;
`POST /v1/empresas` com os dados da empresa 1 (`crt=1` → `opSimplesNacional=ME_EPP`, `regimeEspecialTributacao=NENHUM`,
`codMunIbge`=IBGE da cidade 172) → `empresaId`. Gravar chave+empresaId no `lc_nfse.properties`.

## 5. Formulário (mínimo)

- **Prestador** (read-only, da empresa): CNPJ, razão social, IM, cidade, regime.
- **Serviço:** descrição (`CampoTexto`), código de tributação nacional (`CampoTexto`, default `010101`),
  valor (`CampoValorNumerico`), município de prestação (default = IBGE da empresa, read-only),
  tributação ISSQN (default 1), retenção (default 1).
- **Tomador** (cosmético, só no PDF): nome + CNPJ/CPF.
- **Simular:** combo `AUTORIZADA` / `REJEITADA` (para testar os dois caminhos).

## 6. DANFSE fictício (PDF)

`DanfsePdf` com **iText 2.1.7** (de `lib/`): uma página com cabeçalho "NFS-e (SANDBOX)", prestador,
tomador, serviço, valor, número, chave de acesso, e marca d'água "SANDBOX — SEM VALOR FISCAL". Abre o
PDF com `Desktop.open` ao final.

## 7. Arquitetura & build

- **Camadas (arquivos focados):** `EmpresaDao` (MySQL), `EmpresaInfo` (record), `NfseApiClient` +
  DTOs, `DanfsePdf`, `NfseTela` (UI), `Main` (launcher), `Config` (lê `lc_nfse.properties`).
- **Local:** `C:\PROJETOS\Ruan\NFS-e\lc_nfse\` (mesmo repo git).
- **Build:** `javac` (JDK 17) com classpath em `lib/` (mysql-connector, iText, CampoTexto,
  CampoValorNumerico) → `jar` → **`lc_nfse.jar`**. Um `build.bat` compila; um `run.bat` roda com o
  classpath das libs do LC. **PI.jar intocado.**
- Dependências resolvidas pelos jars da pasta `lib/` do install do LC (referenciadas por caminho).

## 8. Fora de escopo

Transmissão real, DANFSE pixel-perfect do padrão oficial, provisionamento automático, outras telas,
edição de dados da empresa, empacotar as libs (usa as do install).

## 9. Próximos passos

1. Ruan revisa o spec.
2. `writing-plans` detalha: setup do tenant na API → `Config`/`EmpresaDao` → `NfseApiClient` →
   `DanfsePdf` → `NfseTela` → build/run.
