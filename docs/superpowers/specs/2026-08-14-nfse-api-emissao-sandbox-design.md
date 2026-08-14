# Design — API Comercial NFS-e: Emissão em Sandbox (Fatia 2a)

- **Data:** 2026-08-14
- **Autor:** Ruan (LC Sistemas)
- **Status:** Aprovado para virar plano de implementação
- **Contexto:** subsistema #2 (API comercial), fatia 2a. **Foco exclusivo em NFS-e.**
- **Depende de:** #1 (core DPS: build/validate/sign/package) e 2b (auth X-Api-Key + Conta/Empresa).
- **Referência de DX:** Spedy — em especial `guides/regimes-e-codigos-fiscais` (regimes no cadastro da empresa).

---

## 1. Objetivo e critério de sucesso

Endpoint público de emissão de NFS-e em **modo sandbox**: um cliente (chave sandbox) referencia uma
**Empresa** dele e envia os dados do serviço; a API **monta, valida e assina uma DPS real** (reusando
o core do #1) e **simula** a autorização do ADN — sem transmitir. Modelo **async-shaped**
(202 + consulta), alinhado ao Spedy.

**Sucesso:** com chave sandbox, emitir (`202` + id) → consultar (`GET`) e ver `AUTORIZADA` com chave de
acesso simulada (50 díg.) + o XML da DPS assinada; forçar `REJEITADA` via `simular`; dado inválido →
`422`; isolamento por tenant (`404` cross-tenant); chave de produção → `501`.

## 2. Onde mora

Novo pacote `br.com.lc.nfse.api.emissao` (entidade `Emissao`, repo, `EmissaoService`,
`EmissaoController`, `SimuladorNfse`, DTOs). Reusa `core` (#1), `api.tenant` (Empresa) e `api.auth`
(TenantPrincipal). Migrations Flyway **V2** (altera `empresas` — regime) e **V3** (cria `emissoes`).

## 3. Empresa ganha o regime fiscal (alinhado ao Spedy, só NFS-e)

Os regimes são configurados **uma vez no cadastro da Empresa** (não por emissão), como no Spedy.
Mapeiam direto no grupo `regTrib` da DPS:

| Campo na Empresa | Conceito Spedy | Campo DPS | Obrigatório |
|---|---|---|---|
| `opSimplesNacional` (1 não optante / 2 MEI / 3 ME-EPP) | `taxRegime` | `opSimpNac` | Sim |
| `regimeEspecialTributacao` (0 nenhum, 1 cooperativa, 2 estimativa, 3 micro municipal, 4 notário, 5 prof. autônomo, 6 sociedade prof., 9 outros) | `specialTaxRegime` | `regEspTrib` | Sim (default 0) |
| `regimeApuracaoSimplesNacional` (1/2/3) | `simplesNacionalTaxRegime` | `regApTribSN` | Não (só p/ Simples) |

- **Migration V2** adiciona essas colunas em `empresas`.
- **`POST /v1/empresas`** (do 2b) passa a aceitar os 3 campos; `opSimplesNacional` e
  `regimeEspecialTributacao` obrigatórios, `regimeApuracaoSimplesNacional` opcional.
- O `DpsBuilder`/`RequisicaoDpsDto` do #1 **não mudam**: o `EmissaoService` mapeia o regime da
  Empresa (+ dados do request) para o `RequisicaoDpsDto` existente. O #1 fica intacto.

## 4. Domínio — `Emissao`

`id` (UUID), `conta_id`, `empresa_id`, `ambiente`, `status` (PROCESSANDO → AUTORIZADA | REJEITADA),
`chave_acesso` (varchar 50, nullable), `numero_nfse` (nullable), `xml_dps` (text — DPS assinada),
`motivo` (nullable, para rejeição), `criado_em`, `atualizado_em`. Sempre escopado por `conta_id`.

## 5. Contrato

**`POST /v1/nfse`** (chave sandbox):
```json
{
  "empresaId": "<uuid da empresa do cliente>",
  "servico": { "codTribNacional": "010101", "descricao": "Consultoria em TI", "codMunPrestacao": "3550308" },
  "valores": { "valorServico": "1500.00", "tributacaoIssqn": 1, "tipoRetencaoIssqn": 1 },
  "simular": "AUTORIZADA"
}
```
- A **Empresa** fornece a identidade do prestador (CNPJ, cód. município, IM) **e o regime** (§3).
- A API gera `serie`, `numero` (sequencial por empresa), `dhEmi` (agora, UTC) e `dCompet` (hoje).
- `simular` é **opcional** (default `AUTORIZADA`), **só honrado em sandbox**. Valores: `AUTORIZADA`,
  `REJEITADA`.

**`GET /v1/nfse/{id}`** (chave sandbox, escopado): `{ id, status, chaveAcesso, numeroNfse, motivo, xmlDps }`.

## 6. Fluxo de emissão

`POST /v1/nfse`:
1. Auth → `TenantPrincipal (contaId, ambiente)`.
2. **Se ambiente = PRODUCAO → `501`** ("emissão em produção ainda não disponível").
3. Carrega Empresa `(empresaId, contaId)` → `404` se não for da Conta.
4. Monta `RequisicaoDpsDto` (Empresa + request), com `serie`/`numero`/`dhEmi`/`dCompet` gerados.
5. `DpsBuilder` → XML. `DpsValidator` → inválido ⇒ **`422`** (não persiste).
6. `DpsSigner` assina com o **cert sandbox não-ICP** (§7).
7. `SimuladorNfse.simular(simular)` → status + chave de acesso (50 díg.) + número da NFS-e.
8. Persiste `Emissao` com o status final e o XML assinado.
9. Retorna **`202`** `{ id, status }`.

`GET /v1/nfse/{id}`: carrega `(id, contaId)` → `404` se de outra Conta; devolve o registro.

> A simulação é síncrona por baixo (status já final na persistência); o formato **202 + consulta**
> existe para casar com o Spedy e preparar o assíncrono real (2e).

## 7. Certificado de assinatura do sandbox

Reusa `DpsSigner`/`CertificadoLoader` do #1. Um **certificado de teste não-ICP** empacotado em
`src/main/resources/certs/` (claramente sandbox), configurado como o cert padrão de assinatura do
sandbox via `nfse.certificado.*`. Em produção usará o A1 do cliente (fatias 2c/2d).

## 8. `SimuladorNfse`

Dado o `simular` (default `AUTORIZADA`): devolve status + **chave de acesso de 50 dígitos** com cara de
sandbox + número da NFS-e. `REJEITADA` traz um `motivo` fictício. Nada é transmitido.

## 9. Tratamento de erros (envelope do 2b)

`422` (DPS inválida ou `simular` desconhecido) · `404` (empresa/emissão de outra Conta) ·
`501` (chave de produção).

## 10. Testes (Testcontainers, padrão singleton do 2b)

- Cadastrar Empresa com regime → emitir → `202` + id; consultar → `AUTORIZADA` com chave (50 díg.) e
  XML assinado presente.
- `simular=REJEITADA` → consulta `REJEITADA` + motivo.
- Dado inválido (ex.: `codMunPrestacao` malformado) → `422`.
- Cross-tenant: Conta B não consulta emissão da Conta A → `404`.
- Chave de produção → `501`.

## 11. Fora de escopo (Fatia 2a)

Transmissão real ao ADN, cofre de certificados A1, assíncrono/webhooks reais, DANFSE/PDF,
cancelamento/consulta no ADN, emissão em produção, tomador/endereço na DPS (a DPS mínima não exige).

## 12. Próximos passos

1. Ruan revisa este spec.
2. `writing-plans` gera o plano fase a fase (V2 regime na Empresa → V3 emissoes → mapeamento
   DpsBuilder → SimuladorNfse → endpoints → testes), com TDD e Testcontainers.
