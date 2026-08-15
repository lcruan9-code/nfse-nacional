# Import do mapa completo de provedores (ACBr) — roteamento por IBGE

**Data:** 2026-08-15
**Status:** Design aprovado, pronto para plano
**Contexto:** evolui o `provedores_municipais` (criado na fatia ABRASF, migration V5) do seed-amostra
para o **mapa real de 5.571 municípios** do cadastro ACBr.

## 1. Motivação

Hoje o `ResolvedorProvedor` roteia por IBGE sobre uma **amostra de 2 cidades**. Para operar de
verdade — e para a cidade-piloto **Mãe do Rio/PA** (IBGE 1504059, provedor ISSIntel) — precisamos do
mapa real: qual provedor e qual versão ABRASF cada município usa, com os endpoints. Fonte:
`ACBrNFSeXServicos.ini` do projeto ACBr (dados são fatos, não copyrightáveis; o código Pascal é LGPL
e **não** é copiado). O `Provedores-Implementados.txt` complementa provedor→versão.

## 2. Princípio: conhecer ≠ emitir (honestidade)

Importar dá **conhecimento e endpoints prontos**, **não** capacidade de emissão. Nenhum provedor real
é marcado como emitível nesta fatia (decisão do dono: "nenhum real ainda"). A emissão real por
provedor é habilitada deliberadamente, um de cada vez, quando validada (ISSIntel entra na fatia
seguinte). Produção fica 100% verdadeira: **0 cidade real emite** (ainda é modo laboratório).

## 3. Escopo

### Nesta fatia
- Migration **V7**: `ALTER TABLE provedores_municipais ADD COLUMN provedor varchar(40)`.
- Seed real das **~3.079 cidades com provedor mapeado** no INI (as demais ~2.492 têm `Provedor=` vazio
  no ACBr — não são importadas; o resolvedor as trata como ausentes → não suportado).
- Classificação de `tipo` no **gerador do seed** (a "whitelist" vive no gerador, não no runtime):
  - `Provedor=PadraoNacional` → `tipo=ADN`.
  - qualquer outro provedor → `tipo=NAO_SUPORTADO` (guarda `provedor`, `versao_abrasf`, `url_prod`,
    `url_homolog`).
  - **Nenhuma** linha `ABRASF_2X` no seed de produção (whitelist real vazia nesta fatia).
- V7 substitui as 2 linhas-amostra do V5 pelos dados reais (as IBGEs 4204608/1501808/3550308 passam a
  ter seus provedores reais: Betha / Isaneto / ISSSaoPaulo).
- `ResolvedorProvedor`: passa a tratar `tipo=NAO_SUPORTADO` → `CidadeNaoSuportada(ibge, provedor)`;
  o `provedor` é anexado ao resultado (útil para priorizar o que construir a seguir).
- Reprodutibilidade: um **script de geração** (parser do INI → SQL) versionado; o `.sql` gerado é o
  artefato commitado. O INI (38k linhas) **não** é commitado — documenta-se a URL de origem.

### Fora desta fatia
- Habilitar emissão real de qualquer provedor (ISSIntel/ABRASF 1.x = fatia seguinte).
- Detecção completa de ADN (o ACBr só marca 8 como `PadraoNacional`; a lista ADN oficial vem do
  gov.br, é um enriquecimento futuro — não bloqueia).
- Cidades sem provedor no ACBr (não importadas).

### Restrições herdadas
- JDK 17 (sem features Java 21). Nada do caminho ADN alterado. Migrations forward-only.
- Multi-tenant intacto. Sem segredos. Commits pequenos.

## 4. Modelo de dados (após V7)

`provedores_municipais`: `codigo_ibge (PK)`, `nome`, `uf`, **`provedor` (novo, nome bruto ACBr)**,
`tipo` (`ADN` | `ABRASF_2X` | `NAO_SUPORTADO`), `versao_abrasf`, `url_homolog`, `url_prod`,
`estilo_envelope` (default `NFSE_DADOS_MSG`), `algoritmo` (default `SHA1`).

`tipo` continua sendo o discriminador de roteamento em runtime (mínimo refactor); a novidade é o valor
`NAO_SUPORTADO` e a coluna informativa `provedor`.

## 5. Resolvedor (mudança mínima)

`ResolvedorProvedor.mapear(reg)`:
- `tipo == ADN` → `CidadeAdn`.
- `tipo == ABRASF_2X` → `ProvedorResolvido(config)` (inalterado; sem linhas assim em produção nesta fatia).
- `tipo == NAO_SUPORTADO` → `CidadeNaoSuportada(ibge, provedor)`.
- ausente → `CidadeNaoSuportada(ibge, null)`.

`ResolucaoProvedor.CidadeNaoSuportada` ganha um campo opcional `provedor` (String, nullable). A entity
`ProvedorMunicipalRegistro` ganha `provedor` + getter.

## 6. Testes

O seed de produção não tem `ABRASF_2X`; então o caminho "emite" é exercitado por um **fixture de teste
controlado** (IBGE fictício `9999999`, `tipo=ABRASF_2X`, versão 2.04), inserido pelo próprio teste
(idempotente via `ON CONFLICT`, pois `provedores_municipais` não é truncada). Casos:

1. **ResolvedorProvedorTest** (Testcontainers, atualizado para dados reais):
   - `1504208` (Marabá, PadraoNacional) → `CidadeAdn`.
   - `3550308` (São Paulo, ISSSaoPaulo) → `CidadeNaoSuportada` com `provedor="ISSSaoPaulo"`.
   - `1504059` (Mãe do Rio, ISSIntel) → `CidadeNaoSuportada` com `provedor="ISSIntel"`.
   - `9999999` (fixture inserido) → `ProvedorResolvido(ABRASF_2X, 2.04)`.
   - IBGE ausente (ex. `0000000`) → `CidadeNaoSuportada` com `provedor=null`.
2. **EmissaoMunicipalTest** (e2e, atualizado): o caso "emite AUTORIZADA" usa o fixture `9999999`
   (inserido no setup); os casos 409/422 usam cidades reais (`1504208` ADN → 409; `3550308` → 422).
3. **Sanidade do seed** (novo teste leve): após migrations, `count(*) > 3000`, existe ao menos 1 `ADN`,
   e Mãe do Rio (`1504059`) está presente com `provedor='ISSIntel'`.
4. Suíte completa verde (JUnit + Testcontainers), incluindo o `EmissaoMunicipalTest` (nome `*Test`).

## 7. Geração do seed (reprodutível)

Script `scripts/gerar_seed_provedores.py` (ou equivalente): lê `ACBrNFSeXServicos.ini`, para cada
seção `[IBGE]` com `Provedor=` não-vazio extrai `Nome/UF/Provedor/Versao/ProRecepcionar
(→url_prod)/HomRecepcionar (→url_homolog)`, classifica `tipo` (PadraoNacional→ADN, senão
NAO_SUPORTADO), e emite `V7__importar_provedores_acbr.sql` (ALTER + `DELETE FROM provedores_municipais;`
+ INSERTs em lotes). Strings SQL-escapadas. O `.sql` é commitado; o INI não. Documentar a URL de
origem do INI no cabeçalho do script e do `.sql`.

## 8. Riscos / decisões conscientes

- **ADN incompleto:** só 8 cidades marcadas `PadraoNacional` no ACBr. Aceito — a lista ADN real é
  enriquecimento futuro; não marcar uma cidade ADN só faz o resolvedor devolver `NAO_SUPORTADO` (nunca
  um falso "emite"). Seguro.
- **Dados podem envelhecer:** o mapa é um retrato do ACBr na data. O script permite re-sincronizar
  (re-baixar INI + re-gerar). Aceitável.
- **`ON CONFLICT` nos fixtures de teste:** `provedores_municipais` não é truncada; o fixture `9999999`
  é idempotente para não acumular entre testes.
