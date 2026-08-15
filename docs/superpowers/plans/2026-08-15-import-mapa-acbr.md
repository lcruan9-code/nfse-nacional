# Import do mapa ACBr — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Popular `provedores_municipais` com o mapa real de ~3.079 municípios do cadastro ACBr (roteamento por IBGE), de forma honesta (nenhum provedor real marcado como emitível), destravando a piloto Mãe do Rio/PA.

**Architecture:** Coluna nova `provedor`; o resolvedor passa a tratar `tipo=NAO_SUPORTADO`. Duas migrations: **V7** (ALTER, pequena) e **V8** (seed dos 3.079, gerado por script). Os testes deixam de depender do seed (usam fixtures fictícios próprios), então a troca de dados não os quebra.

**Tech Stack:** Java 17, Spring Boot 4.1, JPA + Flyway (PostgreSQL 16), Testcontainers 1.21.4, JUnit 5 + AssertJ, Python 3 (gerador do seed, offline).

## Global Constraints

- Build/roda no **JDK 17**; NUNCA alterar `JAVA_HOME` global. Comandos Maven prefixados com `JAVA_HOME="/c/Program Files/Java/jdk-17"`.
- Docker rodando (Testcontainers). Nada do caminho ADN alterado. Migrations **forward-only** (V7, V8).
- **Honestidade:** nenhuma linha `ABRASF_2X` no seed de produção. `tipo`: `ADN` (Provedor=PadraoNacional) | `NAO_SUPORTADO` (resto).
- `TipoProvedor` (enum) permanece `{ADN, ABRASF_2X}` — `NAO_SUPORTADO` é só valor de string na coluna `tipo`, nunca no enum. `FabricaProvedor` não muda.
- Multi-tenant intacto. Sem segredos. O INI (38k linhas) NÃO é commitado; o `.sql` gerado e o script sim.
- Números reais (já verificados): **3.079 cidades importadas, 8 ADN, 3.071 NAO_SUPORTADO**. Mãe do Rio=`1504059` (ISSIntel); Marabá=`1504208` (ADN).

---

## Task 1: Coluna `provedor` + resolvedor NAO_SUPORTADO + testes desacoplados do seed

**Files:**
- Create: `src/main/resources/db/migration/V7__provedores_add_provedor.sql`
- Modify: `src/main/java/br/com/lc/nfse/core/municipal/registro/ProvedorMunicipalRegistro.java`
- Modify: `src/main/java/br/com/lc/nfse/core/municipal/registro/ResolucaoProvedor.java`
- Modify: `src/main/java/br/com/lc/nfse/core/municipal/registro/ResolvedorProvedor.java`
- Modify: `src/main/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalService.java`
- Modify (rewrite): `src/test/java/br/com/lc/nfse/api/municipal/ResolvedorProvedorTest.java`
- Modify: `src/test/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalTest.java`

**Interfaces:**
- Consumes: `ProvedorConfig`, `TipoProvedor`, `EstiloEnvelope`, `AlgoritmoAssinatura` (existentes).
- Produces: `ResolucaoProvedor.CidadeNaoSuportada(String ibge, String provedor)`; `ProvedorMunicipalRegistro.getProvedor()`; resolvedor que devolve `CidadeAdn`/`ProvedorResolvido`/`CidadeNaoSuportada(ibge,provedor)` conforme a string `tipo`.

- [ ] **Step 1: Migration V7 (ALTER)**

Create `src/main/resources/db/migration/V7__provedores_add_provedor.sql`:
```sql
-- Coluna com o nome bruto do provedor (ACBr): ISSIntel, Fiorilli, Betha, PadraoNacional...
alter table provedores_municipais add column provedor varchar(40);
```

- [ ] **Step 2: Escrever/reescrever o teste do resolvedor (falha)** — usa fixtures próprios, independente do seed

Rewrite `src/test/java/br/com/lc/nfse/api/municipal/ResolvedorProvedorTest.java`:
```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.core.municipal.registro.ResolucaoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolvedorProvedor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedorProvedorTest extends AbstractPostgresIT {

    @Autowired ResolvedorProvedor resolvedor;
    @Autowired JdbcTemplate jdbc;

    /** Semeadura idempotente de um fixture fictício (provedores_municipais não é truncada). */
    private void semear(String ibge, String tipo, String provedor, String versao) {
        jdbc.update("delete from provedores_municipais where codigo_ibge = ?", ibge);
        jdbc.update("insert into provedores_municipais"
                + " (codigo_ibge, nome, uf, tipo, provedor, versao_abrasf, estilo_envelope, algoritmo)"
                + " values (?, 'Fixture', 'PA', ?, ?, ?, 'NFSE_DADOS_MSG', 'SHA1')",
                ibge, tipo, provedor, versao);
    }

    @Test
    void abrasf2xSuportado_resolveComConfig() {
        semear("9999902", "ABRASF_2X", "ProvTeste", "2.04");
        ResolucaoProvedor r = resolvedor.resolver("9999902");
        assertThat(r).isInstanceOf(ResolucaoProvedor.ProvedorResolvido.class);
        var pr = (ResolucaoProvedor.ProvedorResolvido) r;
        assertThat(pr.config().versaoAbrasf()).isEqualTo("2.04");
    }

    @Test
    void adn_deflete() {
        semear("9999901", "ADN", "PadraoNacional", null);
        assertThat(resolvedor.resolver("9999901")).isInstanceOf(ResolucaoProvedor.CidadeAdn.class);
    }

    @Test
    void naoSuportado_devolveProvedor() {
        semear("9999903", "NAO_SUPORTADO", "ISSIntel", "1.00");
        ResolucaoProvedor r = resolvedor.resolver("9999903");
        assertThat(r).isInstanceOf(ResolucaoProvedor.CidadeNaoSuportada.class);
        assertThat(((ResolucaoProvedor.CidadeNaoSuportada) r).provedor()).isEqualTo("ISSIntel");
    }

    @Test
    void ausente_naoSuportadoSemProvedor() {
        ResolucaoProvedor r = resolvedor.resolver("9999900");
        assertThat(r).isInstanceOf(ResolucaoProvedor.CidadeNaoSuportada.class);
        assertThat(((ResolucaoProvedor.CidadeNaoSuportada) r).provedor()).isNull();
    }
}
```

- [ ] **Step 3: Rodar — falha**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./mvnw -q test -Dtest=ResolvedorProvedorTest`
Expected: FAIL de compilação (`CidadeNaoSuportada.provedor()` e `getProvedor` não existem; coluna `provedor` referida).

- [ ] **Step 4: Entity — adicionar `provedor`**

Em `ProvedorMunicipalRegistro.java`, após o campo `private String algoritmo;` adicionar o campo e o getter:
```java
    private String provedor;
```
e, junto aos getters:
```java
    public String getProvedor() { return provedor; }
```

- [ ] **Step 5: `ResolucaoProvedor` — `CidadeNaoSuportada` ganha `provedor`**

Em `ResolucaoProvedor.java`, trocar a linha do record:
```java
    record CidadeNaoSuportada(String ibge, String provedor) implements ResolucaoProvedor {}
```

- [ ] **Step 6: `ResolvedorProvedor` — roteamento por string `tipo`**

Substituir o corpo de `ResolvedorProvedor.java` (métodos `resolver` e `mapear`):
```java
    public ResolucaoProvedor resolver(String ibge) {
        return repo.findById(ibge)
                .map(this::mapear)
                .orElseGet(() -> new ResolucaoProvedor.CidadeNaoSuportada(ibge, null));
    }

    private ResolucaoProvedor mapear(ProvedorMunicipalRegistro reg) {
        String tipo = reg.getTipo();
        if ("ADN".equals(tipo)) {
            return new ResolucaoProvedor.CidadeAdn();
        }
        if ("ABRASF_2X".equals(tipo)) {
            ProvedorConfig cfg = new ProvedorConfig(TipoProvedor.ABRASF_2X, reg.getVersaoAbrasf(),
                    reg.getUrlHomolog(), reg.getUrlProd(),
                    EstiloEnvelope.valueOf(reg.getEstiloEnvelope()),
                    AlgoritmoAssinatura.valueOf(reg.getAlgoritmo()));
            return new ResolucaoProvedor.ProvedorResolvido(cfg);
        }
        return new ResolucaoProvedor.CidadeNaoSuportada(reg.getCodigoIbge(), reg.getProvedor());
    }
```
Os imports de `TipoProvedor`, `EstiloEnvelope`, `AlgoritmoAssinatura`, `ProvedorConfig` já existem e continuam necessários.

- [ ] **Step 7: `EmissaoMunicipalService` — usar o `provedor` na mensagem/log**

Em `resolverConfig(...)`, no trecho do `CidadeNaoSuportada`, enriquecer com o provedor. Trocar:
```java
        ResolucaoProvedor.CidadeNaoSuportada ns = (ResolucaoProvedor.CidadeNaoSuportada) resolucao;
        log.info("IBGE não suportado solicitado: {}", ns.ibge());
        throw new IllegalArgumentException("Município ainda não suportado: " + ns.ibge());
```
por:
```java
        ResolucaoProvedor.CidadeNaoSuportada ns = (ResolucaoProvedor.CidadeNaoSuportada) resolucao;
        log.info("IBGE não suportado solicitado: {} (provedor conhecido: {})", ns.ibge(), ns.provedor());
        throw new IllegalArgumentException("Município ainda não suportado: " + ns.ibge());
```

- [ ] **Step 8: Rodar o teste do resolvedor — verde**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./mvnw -q test -Dtest=ResolvedorProvedorTest`
Expected: PASS (4 testes).

- [ ] **Step 9: Desacoplar o `EmissaoMunicipalTest` do seed (fixtures próprios)**

Em `EmissaoMunicipalTest.java`:
1. Injetar o JdbcTemplate: adicionar o campo `@Autowired JdbcTemplate jdbc;` (import `org.springframework.jdbc.core.JdbcTemplate`).
2. Adicionar o helper (idempotente):
```java
    private void semearProvedor(String ibge, String tipo, String provedor, String versao) {
        jdbc.update("delete from provedores_municipais where codigo_ibge = ?", ibge);
        jdbc.update("insert into provedores_municipais"
                + " (codigo_ibge, nome, uf, tipo, provedor, versao_abrasf, estilo_envelope, algoritmo)"
                + " values (?, 'Fixture', 'PA', ?, ?, ?, 'NFSE_DADOS_MSG', 'SHA1')",
                ibge, tipo, provedor, versao);
    }
```
3. No teste `emiteMunicipalAutorizadaEConsulta`: antes do POST, `semearProvedor("9999902", "ABRASF_2X", "ProvTeste", "2.04");` e trocar o IBGE do JSON de `"4204608"` para `"9999902"` (nos dois usos: emissão e, se houver, no `json(...)`). O restante das asserções (202, `<Signature`, `GerarNfseEnvio`, consulta 200) permanece.
4. No teste `ibgeAdnRetorna409`: `semearProvedor("9999901", "ADN", "PadraoNacional", null);` e usar `"9999901"` no lugar de `"1501808"`.
5. No teste `ibgeDesconhecidoRetorna422`: usar um IBGE garantidamente ausente `"9999900"` no lugar de `"3550308"` (não semear).
6. No teste `crossTenantConsultaRetorna404`: usar `"9999902"` (semeado no próprio teste com `semearProvedor(...)`) para a emissão sob a conta A.
7. No `chaveProducaoRetorna501`: pode manter qualquer IBGE (o 501 é verificado antes do roteamento) — usar `"9999902"` por consistência.

- [ ] **Step 10: Rodar a suíte inteira — verde**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./mvnw -q test`
Expected: BUILD SUCCESS, 0 falhas. (Os testes agora usam fixtures próprios; o seed V5 de 2 linhas ainda existe mas não é mais consultado pelos testes.)

- [ ] **Step 11: Commit**

```bash
git add nfse-nacional/src/main/resources/db/migration/V7__provedores_add_provedor.sql \
        nfse-nacional/src/main/java/br/com/lc/nfse/core/municipal/registro \
        nfse-nacional/src/main/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalService.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/api/municipal/ResolvedorProvedorTest.java \
        nfse-nacional/src/test/java/br/com/lc/nfse/api/municipal/EmissaoMunicipalTest.java
git commit -m "feat: coluna provedor + resolvedor NAO_SUPORTADO; testes desacoplados do seed"
```

---

## Task 2: Seed real (V8) do mapa ACBr + teste de sanidade

**Files:**
- Create: `scripts/gerar_seed_provedores.py`
- Create: `src/main/resources/db/migration/V8__importar_provedores_acbr.sql` (gerado)
- Create: `src/test/java/br/com/lc/nfse/api/municipal/SeedProvedoresTest.java`

**Interfaces:**
- Consumes: schema da V7 (coluna `provedor`).
- Produces: tabela `provedores_municipais` com ~3.079 linhas reais após as migrations.

- [ ] **Step 1: Adicionar o script gerador** `scripts/gerar_seed_provedores.py`

Conteúdo (parser do INI → SQL; classifica `tipo`; lotes de 500; strings SQL-escapadas):
```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gera V8__importar_provedores_acbr.sql a partir do ACBrNFSeXServicos.ini.
Fonte (dados = fatos; codigo Pascal do ACBr e LGPL, NAO copiado):
  https://raw.githubusercontent.com/frones/ACBr/master/Fontes/ACBrDFe/ACBrNFSeX/ACBrNFSeXServicos.ini
Uso: python gerar_seed_provedores.py <ACBrNFSeXServicos.ini> <saida.sql>
"""
import re
import sys

def esc(s):
    return s.replace("'", "''") if s else s

def parse(ini_path):
    linhas = open(ini_path, encoding="latin-1").read().splitlines()
    reg, atual = [], None
    for ln in linhas:
        m = re.match(r"^\[(\d{7})\]\s*$", ln.strip())
        if m:
            if atual:
                reg.append(atual)
            atual = {"ibge": m.group(1)}
            continue
        if atual is None:
            continue
        if "=" in ln and not ln.strip().startswith(";"):
            k, _, v = ln.partition("=")
            atual.setdefault(k.strip(), v.strip())
    if atual:
        reg.append(atual)
    return reg

def gerar(reg):
    linhas = []
    for r in reg:
        prov = r.get("Provedor", "").strip()
        if not prov:
            continue
        tipo = "ADN" if prov == "PadraoNacional" else "NAO_SUPORTADO"
        nome = esc((r.get("Nome", "") or "")[:120])
        uf = esc((r.get("UF", "") or "")[:2])
        provedor = esc(prov[:40])
        versao = esc((r.get("Versao", "") or "")[:6]) or None
        url_prod = esc((r.get("ProRecepcionar", "") or "")[:400]) or None
        url_hom = esc((r.get("HomRecepcionar", "") or "")[:400]) or None
        def q(v):
            return "'%s'" % v if v is not None else "NULL"
        linhas.append("('%s','%s','%s','%s',%s,%s,%s,%s,'NFSE_DADOS_MSG','SHA1')"
                      % (r["ibge"], nome, uf, tipo, q(provedor), q(versao), q(url_hom), q(url_prod)))
    return linhas

COLS = "  (codigo_ibge, nome, uf, tipo, provedor, versao_abrasf, url_homolog, url_prod, estilo_envelope, algoritmo)\nvalues\n"

def main():
    ini, saida = sys.argv[1], sys.argv[2]
    valores = gerar(parse(ini))
    with open(saida, "w", encoding="utf-8", newline="\n") as f:
        f.write("-- V8: import do mapa de provedores municipais (cadastro ACBr, ACBrNFSeXServicos.ini).\n")
        f.write("-- Dados = fatos. Fonte: github.com/frones/ACBr .../ACBrNFSeXServicos.ini\n")
        f.write("-- Gerado por scripts/gerar_seed_provedores.py. tipo: ADN (PadraoNacional) | NAO_SUPORTADO.\n\n")
        f.write("delete from provedores_municipais;\n\n")
        f.write("insert into provedores_municipais\n" + COLS)
        for i in range(0, len(valores), 500):
            f.write(",\n".join(valores[i:i + 500]))
            f.write(";\n" if i + 500 >= len(valores) else ";\n\ninsert into provedores_municipais\n" + COLS)
    adn = sum(1 for v in valores if "'ADN'" in v)
    print("cidades: %d | ADN: %d | NAO_SUPORTADO: %d" % (len(valores), adn, len(valores) - adn))

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Baixar o INI e gerar a V8**

Run:
```bash
cd nfse-nacional
curl -sSL -o /tmp/acbr.ini "https://raw.githubusercontent.com/frones/ACBr/master/Fontes/ACBrDFe/ACBrNFSeX/ACBrNFSeXServicos.ini"
python ../scripts/gerar_seed_provedores.py /tmp/acbr.ini src/main/resources/db/migration/V8__importar_provedores_acbr.sql
```
Expected: imprime `cidades: 3079 | ADN: 8 | NAO_SUPORTADO: 3071` (números podem variar levemente se o ACBr atualizar o INI). Confere: `grep -c "^('" ...` ≈ 3079; `grep "'1504059'" ...V8...` mostra Mãe do Rio/ISSIntel.

- [ ] **Step 3: Escrever o teste de sanidade do seed (falha)**

Create `src/test/java/br/com/lc/nfse/api/municipal/SeedProvedoresTest.java`:
```java
package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Sanidade do seed real (V8): o mapa ACBr foi aplicado pelo Flyway. */
class SeedProvedoresTest extends AbstractPostgresIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    void seedRealAplicado() {
        Integer total = jdbc.queryForObject("select count(*) from provedores_municipais", Integer.class);
        assertThat(total).isGreaterThan(3000);

        Integer adn = jdbc.queryForObject(
                "select count(*) from provedores_municipais where tipo = 'ADN'", Integer.class);
        assertThat(adn).isGreaterThanOrEqualTo(1);

        String provMaeDoRio = jdbc.queryForObject(
                "select provedor from provedores_municipais where codigo_ibge = '1504059'", String.class);
        assertThat(provMaeDoRio).isEqualTo("ISSIntel");
    }
}
```

- [ ] **Step 4: Rodar — falha**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./mvnw -q test -Dtest=SeedProvedoresTest`
Expected: FAIL antes da V8 existir/ser aplicada (Mãe do Rio ausente ou count baixo). Após o Step 2 ter gerado a V8, deve PASSAR — se rodar o Step 4 já com a V8 presente, ele passa direto (a "falha" é conceitual; o gate real é o Step 5).

- [ ] **Step 5: Rodar a suíte inteira — verde**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./mvnw -q test`
Expected: BUILD SUCCESS, 0 falhas. O `delete from provedores_municipais` da V8 roda uma vez (migration); os testes que inserem fixtures `9999xxx` o fazem em runtime, depois das migrations — sem conflito com as ~3.079 linhas reais.

- [ ] **Step 6: Commit**

```bash
git add scripts/gerar_seed_provedores.py \
        nfse-nacional/src/main/resources/db/migration/V8__importar_provedores_acbr.sql \
        nfse-nacional/src/test/java/br/com/lc/nfse/api/municipal/SeedProvedoresTest.java
git commit -m "feat: seed do mapa ACBr (V8, ~3079 municipios por IBGE) + sanidade"
```

---

## Self-Review (na escrita do plano)

**Cobertura do spec:** V7 coluna provedor (T1) ✔; NAO_SUPORTADO no resolvedor (T1) ✔; CidadeNaoSuportada com provedor (T1) ✔; seed real V8 honesto sem ABRASF_2X (T2) ✔; substitui V5 via `delete` (T2) ✔; testes com cidades/fixtures e sanidade (T1+T2) ✔; script versionado / INI fora do repo (T2) ✔.

**Placeholders:** nenhum; todo passo tem código/comando real e números verificados (3079/8/3071).

**Consistência de tipos:** `CidadeNaoSuportada(ibge, provedor)` usado igual no resolvedor, no service e nos testes; `getProvedor()` no entity; `tipo` como string no resolvedor (enum `TipoProvedor` inalterado, `FabricaProvedor` intocado).

**Risco anotado:** os testes inserem fixtures em `provedores_municipais` (não truncada) de forma idempotente (`delete`+`insert` por IBGE fictício) — não acumulam nem colidem com o seed real.
