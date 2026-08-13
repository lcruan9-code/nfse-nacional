# Design — API Comercial NFS-e: Auth + Multi-tenant (Fatia 2b)

- **Data:** 2026-08-13
- **Autor:** Ruan (LC Sistemas)
- **Status:** Aprovado para virar plano de implementação
- **Contexto:** subsistema #2 (API comercial) do projeto NFS-e Padrão Nacional. **Foco exclusivo em NFS-e.**
- **Referência de DX:** Spedy (https://docs.spedy.com.br) — `X-Api-Key`, sandbox+produção, async+webhooks, doc em Getting Started / API Reference / Webhooks.

---

## 1. Contexto e decomposição do subsistema #2

O subsistema #2 (API comercial de NFS-e) é grande e foi fatiado:

| Fatia | O que é | Quando |
|-------|---------|--------|
| **2b** | **Auth (`X-Api-Key`) + multi-tenant (Conta → Empresas)** | **Agora (este spec)** |
| 2a | Contrato público + Sandbox simulado (valida/persiste/simula ciclo + webhook) | Depois da fundação de acesso |
| 2c | Cofre de certificados A1 (upload + AES-256-GCM em repouso) | Antes da emissão real |
| 2d | Transmissão real ao ADN em produção (o "passo cego" já pronto no #1) | Quando houver A1 |
| 2e | Assíncrono + webhooks reais + retry/resiliência | Com volume |

A Fatia **2b** vem primeiro: é preciso saber **quem** chama antes de tratar emissões e certificados.

## 2. Objetivo e critério de sucesso

Fundação de acesso da API: autenticação por `X-Api-Key` + multi-tenant de dois níveis
(**Conta → Empresas**) em PostgreSQL, com Spring Security. Provisionamento **admin/seed** (sem signup público).

**Sucesso:** uma requisição com chave válida é autenticada, resolve **Conta + ambiente**, e só
enxerga/gerencia as **próprias** Empresas — provado por testes, incluindo **isolamento cross-tenant**
(Conta A não acessa Empresa da Conta B → 404).

## 3. Onde mora (estrutura)

Mesmo app `nfse-nacional`, pacotes novos — reusa o `core` fiscal e **mantém o
`web.DpsPreviewController` atual intacto**:

```
br.com.lc.nfse
  core/            # fiscal (inalterado)
  web/             # preview atual (inalterado)
  api/
    tenant/        # Conta, Empresa: entidades, repos, services
    auth/          # ApiKey, ApiKeyAuthFilter, SecurityConfig, TenantPrincipal
    web/           # AdminContaController, EmpresaController, WhoamiController
    error/         # envelope de erro + handler
```

**⚠️ Impacto:** adicionar JPA+Postgres faz o app exigir banco para subir. O `contextLoads`
(@SpringBootTest) atual passa a precisar de um datasource de teste (Testcontainers — ver §11).

## 4. Modelo de domínio

- **Conta** (tenant): `id` UUID (PK), `nome`, `status` (ATIVA|SUSPENSA), `criado_em`, `atualizado_em`.
- **Empresa**: `id` UUID (PK), `conta_id` FK→Conta, `cnpj` (14 díg.), `razao_social`,
  `inscricao_municipal` (nullable), `cod_mun_ibge` (7 díg.), `status`, timestamps.
  Restrição única `(conta_id, cnpj)`.
- **ApiKey**: `id` UUID (PK), `conta_id` FK→Conta, `ambiente` (SANDBOX|PRODUCAO),
  `key_hash` (SHA-256 hex, único, indexado), `key_prefix` (ex.: `sk_test_ab12cd`),
  `status` (ATIVA|REVOGADA), `criado_em`, `ultimo_uso_em` (nullable).

Relações: Conta 1..* Empresa; Conta 1..* ApiKey.

## 5. Formato e segurança das chaves

- Formato (estilo Stripe): `sk_test_<aleatório>` (SANDBOX) / `sk_live_<aleatório>` (PRODUCAO).
  O prefixo codifica o ambiente; `<aleatório>` = 32 bytes de `SecureRandom`, base62/hex.
- **Nunca** persistir a chave em texto: guardar só o **SHA-256** (`key_hash`, único/indexado)
  e o `key_prefix` curto (exibição/diagnóstico). A chave em claro é retornada **uma única vez**,
  na criação da Conta/chave.
- Chaves são **revogáveis** (status = REVOGADA).

## 6. Fluxo de auth (Spring Security)

`ApiKeyAuthFilter` (OncePerRequestFilter):
1. Lê o header `X-Api-Key`. Ausente em rota protegida → 401.
2. Deriva o ambiente pelo prefixo (`sk_test_` / `sk_live_`).
3. Calcula SHA-256 e busca `ApiKey` ativa por `key_hash`.
4. Não achou / revogada → 401. Achou → popula `SecurityContext` com `TenantPrincipal`
   (`contaId`, `ambiente`) e authorities (`ROLE_TENANT`).
5. Atualiza `ultimo_uso_em` (throttled — no máximo 1x/min por chave, para não escrever a cada request).

`SecurityFilterChain`: `/health` liberado; `/admin/**` protegido por credencial de admin (§7);
`/v1/**` exige `ROLE_TENANT`; demais negado. CSRF desabilitado (API stateless), sessão STATELESS.

## 7. Endpoints desta fatia

**Admin** — protegido por `X-Admin-Key` (header) comparado em tempo constante contra
`nfse.admin.api-key` (config/env). Simples e suficiente para a fatia.
- `POST /admin/contas` → cria Conta + emite **par de chaves** (sandbox+prod).
  Resposta inclui as **chaves em texto (uma única vez)** + os prefixos.

**Tenant** — auth via `X-Api-Key` (`ROLE_TENANT`):
- `GET /v1/whoami` → `{ contaId, nomeConta, ambiente }` (prova a auth e o ambiente resolvido).
- `POST /v1/empresas` → cria Empresa sob a Conta autenticada. Valida CNPJ/IBGE. 201.
- `GET /v1/empresas` → lista Empresas **da Conta**.
- `GET /v1/empresas/{id}` → detalhe; **404** se pertencer a outra Conta.

## 8. Ambientes (sandbox × produção)

O ambiente é **resolvido pela chave** e carregado no `TenantPrincipal`. As **Empresas pertencem à
Conta** (não são duplicadas por ambiente); as **emissões** (fatia futura) serão marcadas por ambiente.
Simplificação deliberada — evita duplicar cadastro agora.

## 9. Isolamento por tenant

Toda leitura/escrita de Empresa é escopada pelo `contaId` do principal
(ex.: `empresaRepository.findByIdAndContaId(id, contaId)`). Acesso a recurso de outra Conta →
**404** (não 403, para não vazar existência). Coberto por teste de isolamento.

## 10. Persistência e migrations

- Spring Data JPA + **PostgreSQL 16**. PKs **UUID** (geradas na aplicação).
- **Flyway** para o schema: `V1__contas_empresas_apikeys.sql` (tabelas + índices + FKs +
  únicos `(conta_id, cnpj)` e `key_hash`).
- **Docker (dev/local):** container **`nfse-postgres`** (imagem `postgres:16`), volume persistente
  **`nfse-pgdata`**, database/usuário `nfse`, porta `5432`.
- Conexão configurada no profile `homolog` (default do app), apontando para o Postgres local;
  credenciais por env var. Mantém os profiles `homolog`/`prod` já existentes do #1.

## 11. Tratamento de erros

Envelope consistente: `{ "erro": { "codigo": "<slug>", "mensagem": "<legível>" } }`.
`@RestControllerAdvice` mapeia: 401 (auth ausente/inválida), 404 (não encontrado/cross-tenant),
422 (validação, ex.: CNPJ com formato inválido), 409 (CNPJ já cadastrado na Conta).

## 12. Testes

- **Testcontainers PostgreSQL 16** (banco real, Flyway roda de verdade).
- Casos:
  - auth: chave válida → `whoami` 200 com ambiente certo; ausente/inválida/revogada → 401;
    prefixo `sk_test_` resolve SANDBOX, `sk_live_` resolve PRODUCAO.
  - Empresa: cria (201), lista só as da Conta, detalhe 200.
  - **isolamento cross-tenant:** Conta A não vê/acessa Empresa da Conta B → 404.
  - admin: `POST /admin/contas` com `X-Admin-Key` correto cria Conta e retorna chaves uma vez;
    admin key errada → 401.
- O `contextLoads` (@SpringBootTest) passa a subir com o datasource do Testcontainers.

## 13. Ambiente e pré-requisitos

- **Docker Desktop** disponível (confirmado pelo Ruan) — usado para o Postgres local e o Testcontainers.
- **Fase 0 do plano:** subir o container `nfse-postgres` + volume `nfse-pgdata` (persistente) e
  validar conexão, antes de qualquer código.
- Build segue no **JDK 17** (não no Java 8 global), Maven via wrapper (herdado do #1).

## 14. Fora de escopo (Fatia 2b)

Fluxo de emissão, cofre de certificados A1, signup público self-service, transmissão real,
rate limiting, billing, webhooks, painel/UI. Todos em fatias posteriores.

## 15. Próximos passos

1. Ruan revisa este spec.
2. `writing-plans` gera o plano fase a fase (Fase 0 = Docker Postgres; depois migrations, entidades,
   auth, endpoints, isolamento), com TDD e Testcontainers.
