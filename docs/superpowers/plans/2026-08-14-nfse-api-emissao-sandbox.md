# API Comercial NFS-e — Emissão em Sandbox (Fatia 2a) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Endpoint público de emissão de NFS-e em sandbox — monta/valida/assina uma DPS real (reusa o core do #1) e simula a autorização do ADN (async-shaped: 202 + consulta), com o regime fiscal no cadastro da Empresa.

**Architecture:** Novo pacote `api.emissao` sobre o #1 (core DPS) e o 2b (auth + Empresa). A Empresa ganha o `regTrib` (alinhado ao Spedy). `EmissaoService` mapeia Empresa+request → `RequisicaoDpsDto`, roda o pipeline do #1, e um `SimuladorNfse` decide o resultado. Postgres + Flyway (V2 altera empresas, V3 cria emissoes), Testcontainers.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring Security, JPA, Flyway, PostgreSQL 16, Testcontainers 1.21.4, JUnit 5.

## Global Constraints

- Build no **JDK 17** (não Java 8), Maven via wrapper. Postgres dev no Docker **`nfse-postgres` porta 55432**.
- **#1 intacto:** não alterar `core` nem `RequisicaoDpsDto`/`DpsBuilder`. Só consumir.
- **Só NFS-e.** Sandbox apenas; chave de produção → 501.
- **Chave nunca em texto.** Isolamento por tenant em toda query (`conta_id`), cross-tenant → 404.
- **TDD**, testes de integração via `AbstractPostgresIT` (singleton), classes de teste terminam em `Test`.
- `@Value("${local.server.port}")` para a porta (não `@LocalServerPort`). Commits locais, sem push.
- Cert de assinatura do sandbox: `.p12` não-ICP em `src/main/resources/certs/` (liberar no `.gitignore`).

---

## FASE 1 — Empresa ganha o regime fiscal

### Task 1.1: Enums de regime + migration V2 + campos na Empresa

**Files:**
- Create: `api/tenant/OpSimplesNacional.java`, `api/tenant/RegimeEspecialTributacao.java`, `api/tenant/RegimeApuracaoSimplesNacional.java`
- Create: `src/main/resources/db/migration/V2__empresas_regime.sql`
- Modify: `api/tenant/Empresa.java` (campos + factory)

- [ ] **Step 1: Enums** (cada um carrega o código DPS):

```java
package br.com.lc.nfse.api.tenant;

public enum OpSimplesNacional {
    NAO_OPTANTE(1), MEI(2), ME_EPP(3);
    private final int codigo;
    OpSimplesNacional(int c) { this.codigo = c; }
    public int codigo() { return codigo; }
}
```

```java
package br.com.lc.nfse.api.tenant;

public enum RegimeEspecialTributacao {
    NENHUM(0), COOPERATIVA(1), ESTIMATIVA(2), MICRO_MUNICIPAL(3),
    NOTARIO(4), PROF_AUTONOMO(5), SOCIEDADE_PROFISSIONAIS(6), OUTROS(9);
    private final int codigo;
    RegimeEspecialTributacao(int c) { this.codigo = c; }
    public int codigo() { return codigo; }
}
```

```java
package br.com.lc.nfse.api.tenant;

public enum RegimeApuracaoSimplesNacional {
    FEDERAL_MUNICIPAL_SN(1), FEDERAL_SN_ISSQN_NFSE(2), FEDERAL_MUNICIPAL_NFSE(3);
    private final int codigo;
    RegimeApuracaoSimplesNacional(int c) { this.codigo = c; }
    public int codigo() { return codigo; }
}
```

- [ ] **Step 2: Migration `V2__empresas_regime.sql`**

```sql
alter table empresas add column op_simples_nacional varchar(20) not null default 'NAO_OPTANTE';
alter table empresas add column regime_especial_tributacao varchar(30) not null default 'NENHUM';
alter table empresas add column regime_apuracao_simples_nacional varchar(30);
```

- [ ] **Step 3: Campos + factory na `Empresa`** — adicionar:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "op_simples_nacional", nullable = false)
    private OpSimplesNacional opSimplesNacional;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_especial_tributacao", nullable = false)
    private RegimeEspecialTributacao regimeEspecialTributacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_apuracao_simples_nacional")
    private RegimeApuracaoSimplesNacional regimeApuracaoSimplesNacional;
```

Substituir a factory `nova(...)` por (mantendo a ordem dos campos existentes + regime ao final):

```java
    public static Empresa nova(UUID contaId, String cnpj, String razaoSocial, String inscricaoMunicipal,
                               String codMunIbge, OpSimplesNacional opSimplesNacional,
                               RegimeEspecialTributacao regimeEspecialTributacao,
                               RegimeApuracaoSimplesNacional regimeApuracaoSimplesNacional,
                               OffsetDateTime agora) {
        Empresa e = new Empresa();
        e.id = UUID.randomUUID();
        e.contaId = contaId; e.cnpj = cnpj; e.razaoSocial = razaoSocial;
        e.inscricaoMunicipal = inscricaoMunicipal; e.codMunIbge = codMunIbge;
        e.opSimplesNacional = opSimplesNacional;
        e.regimeEspecialTributacao = regimeEspecialTributacao;
        e.regimeApuracaoSimplesNacional = regimeApuracaoSimplesNacional;
        e.status = StatusEmpresa.ATIVA; e.criadoEm = agora; e.atualizadoEm = agora;
        return e;
    }
```

Adicionar getters: `getOpSimplesNacional()`, `getRegimeEspecialTributacao()`, `getRegimeApuracaoSimplesNacional()`.

### Task 1.2: `POST /v1/empresas` aceita o regime + ajustar chamadas de `nova(...)`

**Files:**
- Modify: `api/web/dto/CriarEmpresaRequest.java`, `api/tenant/EmpresaService.java`
- Modify (call sites): `api/tenant/EmpresaRepositoryTest.java`, `api/web/EmpresaTest.java`

- [ ] **Step 1: `CriarEmpresaRequest`** — adicionar campos de regime:

```java
public record CriarEmpresaRequest(
        String cnpj, String razaoSocial, String inscricaoMunicipal, String codMunIbge,
        OpSimplesNacional opSimplesNacional,
        RegimeEspecialTributacao regimeEspecialTributacao,
        RegimeApuracaoSimplesNacional regimeApuracaoSimplesNacional) {}
```
(importar os enums de `br.com.lc.nfse.api.tenant`.)

- [ ] **Step 2: `EmpresaService.criar`** — validar `opSimplesNacional` obrigatório; default do especial:

```java
    public EmpresaResponse criar(UUID contaId, CriarEmpresaRequest req) {
        validar(req);
        if (repositorio.existsByContaIdAndCnpj(contaId, req.cnpj())) {
            throw new ConflitoException("CNPJ já cadastrado nesta conta: " + req.cnpj());
        }
        RegimeEspecialTributacao especial = req.regimeEspecialTributacao() != null
                ? req.regimeEspecialTributacao() : RegimeEspecialTributacao.NENHUM;
        Empresa e = repositorio.save(Empresa.nova(contaId, req.cnpj(), req.razaoSocial(),
                req.inscricaoMunicipal(), req.codMunIbge(), req.opSimplesNacional(), especial,
                req.regimeApuracaoSimplesNacional(), OffsetDateTime.now()));
        return toResponse(e);
    }
```
No `validar(...)`, acrescentar: `if (req.opSimplesNacional() == null) throw new IllegalArgumentException("opSimplesNacional é obrigatório");`.

- [ ] **Step 3: Ajustar as chamadas de `Empresa.nova(...)` nos testes** — `EmpresaRepositoryTest` e `EmpresaTest` passam a incluir o regime. Exemplo:

```java
Empresa.nova(a.getId(), "11222333000181", "Emp A1", null, "3550308",
        OpSimplesNacional.NAO_OPTANTE, RegimeEspecialTributacao.NENHUM, null, agora)
```
No corpo JSON do `EmpresaTest`, acrescentar `"opSimplesNacional":"NAO_OPTANTE"`.

- [ ] **Step 4: Rodar** `.\mvnw.cmd -B "-Dtest=EmpresaRepositoryTest,EmpresaTest" test`. Expected: PASS.

- [ ] **Step 5: Commit** `feat(2a): Empresa carrega regTrib (regime) alinhado ao Spedy + migration V2`.

---

## FASE 2 — Entidade `Emissao`

### Task 2.1: Migration V3 + entidade + repo

**Files:**
- Create: `src/main/resources/db/migration/V3__emissoes.sql`
- Create: `api/emissao/StatusEmissao.java`, `api/emissao/Emissao.java`, `api/emissao/EmissaoRepository.java`

- [ ] **Step 1: Migration `V3__emissoes.sql`**

```sql
create table emissoes (
    id            uuid primary key,
    conta_id      uuid not null references contas(id),
    empresa_id    uuid not null references empresas(id),
    ambiente      varchar(10) not null,
    status        varchar(20) not null,
    chave_acesso  varchar(50),
    numero_nfse   varchar(20),
    xml_dps       text,
    motivo        varchar(400),
    criado_em     timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);
create index ix_emissoes_conta on emissoes(conta_id);
create index ix_emissoes_empresa on emissoes(empresa_id);
```

- [ ] **Step 2: `StatusEmissao`** — `enum StatusEmissao { PROCESSANDO, AUTORIZADA, REJEITADA }`.

- [ ] **Step 3: `Emissao`** entity — campos `id, contaId (conta_id), empresaId (empresa_id), ambiente (Ambiente, STRING), status (StatusEmissao, STRING), chaveAcesso (chave_acesso), numeroNfse (numero_nfse), xmlDps (xml_dps), motivo, criadoEm, atualizadoEm`. Factory:

```java
public static Emissao autorizada(UUID contaId, UUID empresaId, Ambiente ambiente,
        String chaveAcesso, String numeroNfse, String xmlDps, OffsetDateTime agora) { ... status = AUTORIZADA ... }

public static Emissao rejeitada(UUID contaId, UUID empresaId, Ambiente ambiente,
        String motivo, String xmlDps, OffsetDateTime agora) { ... status = REJEITADA ... }
```
Getters para todos os campos.

- [ ] **Step 4: `EmissaoRepository`** extends `JpaRepository<Emissao, UUID>` com
  `Optional<Emissao> findByIdAndContaId(UUID id, UUID contaId)` e
  `long countByEmpresaId(UUID empresaId)`.

- [ ] **Step 5: Commit** `feat(2a): entidade Emissao + migration V3`.

---

## FASE 3 — `SimuladorNfse`

### Task 3.1: Simulador do resultado

**Files:** Create `api/emissao/SimuladorNfse.java`, `api/emissao/ResultadoSimulado.java`; Test `api/emissao/SimuladorNfseTest.java`

**Interfaces:**
- Produces: `record ResultadoSimulado(StatusEmissao status, String chaveAcesso, String numeroNfse, String motivo)`;
  `SimuladorNfse.simular(String simular, String numeroNfse) : ResultadoSimulado`.

- [ ] **Step 1: Teste falhando** `SimuladorNfseTest`:

```java
package br.com.lc.nfse.api.emissao;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimuladorNfseTest {
    private final SimuladorNfse sim = new SimuladorNfse();

    @Test
    void defaultAutoriza() {
        ResultadoSimulado r = sim.simular(null, "1");
        assertEquals(StatusEmissao.AUTORIZADA, r.status());
        assertEquals(50, r.chaveAcesso().length());
        assertTrue(r.chaveAcesso().chars().allMatch(Character::isDigit));
        assertNull(r.motivo());
    }

    @Test
    void simularRejeitadaRejeita() {
        ResultadoSimulado r = sim.simular("REJEITADA", "1");
        assertEquals(StatusEmissao.REJEITADA, r.status());
        assertNotNull(r.motivo());
        assertNull(r.chaveAcesso());
    }

    @Test
    void simularDesconhecidoLancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> sim.simular("XPTO", "1"));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar** (`-Dtest=SimuladorNfseTest`).

- [ ] **Step 3: Implementar** `ResultadoSimulado` e `SimuladorNfse`:

```java
package br.com.lc.nfse.api.emissao;

public record ResultadoSimulado(StatusEmissao status, String chaveAcesso, String numeroNfse, String motivo) {}
```

```java
package br.com.lc.nfse.api.emissao;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;

@Service
public class SimuladorNfse {

    private final SecureRandom random = new SecureRandom();

    public ResultadoSimulado simular(String simular, String numeroNfse) {
        String pedido = (simular == null || simular.isBlank()) ? "AUTORIZADA" : simular.trim().toUpperCase();
        return switch (pedido) {
            case "AUTORIZADA" -> new ResultadoSimulado(StatusEmissao.AUTORIZADA, chaveFake(), numeroNfse, null);
            case "REJEITADA" -> new ResultadoSimulado(StatusEmissao.REJEITADA, null, null,
                    "Rejeição simulada no ambiente de sandbox (E9999)");
            default -> throw new IllegalArgumentException("simular inválido: " + simular + " (use AUTORIZADA ou REJEITADA)");
        };
    }

    private String chaveFake() {
        StringBuilder sb = new StringBuilder(50);
        for (int i = 0; i < 50; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
```

- [ ] **Step 4: Rodar e ver passar. Commit** `feat(2a): SimuladorNfse (autorizada/rejeitada + chave 50 dígitos)`.

---

## FASE 4 — Certificado de assinatura do sandbox

### Task 4.1: Gerar cert sandbox em main/resources + config

**Files:**
- Create: `src/main/resources/certs/sandbox.p12`
- Modify: `.gitignore` (liberar o sandbox.p12), `src/main/resources/application.yml`

- [ ] **Step 1: Gerar o cert** (keytool do JDK 17):

```powershell
$dir = "C:\PROJETOS\Ruan\NFS-e\nfse-nacional\src\main\resources\certs"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
& "C:\Program Files\Java\jdk-17\bin\keytool.exe" -genkeypair -alias sandbox -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore "$dir\sandbox.p12" -storepass sandbox -keypass sandbox -dname "CN=NFSE SANDBOX NAO-ICP, OU=DEV, O=LC Sistemas, C=BR" -validity 3650
```

- [ ] **Step 2: `.gitignore`** — acrescentar exceção:

```gitignore
!nfse-nacional/src/main/resources/certs/sandbox.p12
```

- [ ] **Step 3: `application.yml`** — apontar o cert padrão de assinatura:

```yaml
nfse:
  admin:
    api-key: ${NFSE_ADMIN_KEY:admin_dev_key}
  certificado:
    caminho: ${NFSE_CERT_CAMINHO:classpath:certs/sandbox.p12}
    senha: ${NFSE_CERT_SENHA:sandbox}
```

- [ ] **Step 4: Commit** `chore(2a): certificado sandbox nao-ICP + config de assinatura`.

---

## FASE 5 — Serviço + endpoints de emissão

### Task 5.1: DTOs + `EmissaoService` + `EmissaoController`

**Files:**
- Create: `api/emissao/dto/EmitirNfseRequest.java`, `api/emissao/dto/EmissaoResponse.java`
- Create: `api/emissao/EmissaoService.java`, `api/emissao/EmissaoController.java`
- Create: `api/emissao/ProducaoIndisponivelException.java` + handler no `ApiExceptionHandler`
- Test: `api/emissao/EmissaoSandboxTest.java`

**Interfaces:**
- Consumes: `DpsBuilder`, `DpsValidator`, `DpsSigner`, `DpsPackager`, `RequisicaoDpsDto` (core #1);
  `CertificadoProvider` (web #1); `EmpresaRepository`, `EmissaoRepository`, `SimuladorNfse`; `TenantPrincipal`.
- Produces: `POST /v1/nfse` → 202 `EmissaoResponse`; `GET /v1/nfse/{id}` → `EmissaoResponse`.

- [ ] **Step 1: DTOs**

```java
package br.com.lc.nfse.api.emissao.dto;
import java.util.UUID;
public record EmitirNfseRequest(UUID empresaId, Servico servico, Valores valores, String simular) {
    public record Servico(String codTribNacional, String descricao, String codMunPrestacao) {}
    public record Valores(String valorServico, int tributacaoIssqn, int tipoRetencaoIssqn) {}
}
```

```java
package br.com.lc.nfse.api.emissao.dto;
import java.util.UUID;
public record EmissaoResponse(UUID id, String status, String chaveAcesso, String numeroNfse,
                              String motivo, String xmlDps) {}
```

- [ ] **Step 2: Exceção 501** `ProducaoIndisponivelException extends RuntimeException` + no `ApiExceptionHandler`:

```java
    @ExceptionHandler(br.com.lc.nfse.api.emissao.ProducaoIndisponivelException.class)
    public ResponseEntity<EnvelopeErro> producao(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(EnvelopeErro.de("producao_indisponivel", e.getMessage()));
    }
```

- [ ] **Step 3: `EmissaoService`** — orquestra o pipeline:

```java
package br.com.lc.nfse.api.emissao;

import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.emissao.dto.EmissaoResponse;
import br.com.lc.nfse.api.emissao.dto.EmitirNfseRequest;
import br.com.lc.nfse.api.error.NaoEncontradoException;
import br.com.lc.nfse.api.tenant.Empresa;
import br.com.lc.nfse.api.tenant.EmpresaRepository;
import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.dps.DpsBuilder;
import br.com.lc.nfse.core.dps.DpsSigner;
import br.com.lc.nfse.core.dps.DpsValidator;
import br.com.lc.nfse.core.dps.RequisicaoDpsDto;
import br.com.lc.nfse.core.dps.ResultadoValidacao;
import br.com.lc.nfse.web.CertificadoProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class EmissaoService {

    private final EmpresaRepository empresaRepository;
    private final EmissaoRepository emissaoRepository;
    private final DpsBuilder dpsBuilder;
    private final DpsValidator dpsValidator;
    private final DpsSigner dpsSigner;
    private final CertificadoProvider certificadoProvider;
    private final SimuladorNfse simulador;

    public EmissaoService(EmpresaRepository empresaRepository, EmissaoRepository emissaoRepository,
                          DpsBuilder dpsBuilder, DpsValidator dpsValidator, DpsSigner dpsSigner,
                          CertificadoProvider certificadoProvider, SimuladorNfse simulador) {
        this.empresaRepository = empresaRepository;
        this.emissaoRepository = emissaoRepository;
        this.dpsBuilder = dpsBuilder;
        this.dpsValidator = dpsValidator;
        this.dpsSigner = dpsSigner;
        this.certificadoProvider = certificadoProvider;
        this.simulador = simulador;
    }

    @Transactional
    public EmissaoResponse emitir(TenantPrincipal principal, EmitirNfseRequest req) {
        if (principal.ambiente() != Ambiente.SANDBOX) {
            throw new ProducaoIndisponivelException("Emissão em produção ainda não disponível");
        }
        Empresa empresa = empresaRepository.findByIdAndContaId(req.empresaId(), principal.contaId())
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada: " + req.empresaId()));

        String numero = String.valueOf(emissaoRepository.countByEmpresaId(empresa.getId()) + 1);
        RequisicaoDpsDto dto = montarDto(empresa, req, numero);

        String xml = dpsBuilder.construir(dto);
        ResultadoValidacao validacao = dpsValidator.validar(xml);
        if (!validacao.valido()) {
            throw new IllegalArgumentException("DPS inválida: " + validacao.mensagem());
        }
        CertificadoLoader.Certificado cert = certificadoProvider.obter()
                .orElseThrow(() -> new IllegalStateException("Certificado de assinatura do sandbox não configurado"));
        String assinado = dpsSigner.assinar(xml, cert);

        ResultadoSimulado sim = simulador.simular(req.simular(), numero);
        OffsetDateTime agora = OffsetDateTime.now();
        Emissao emissao = sim.status() == StatusEmissao.AUTORIZADA
                ? Emissao.autorizada(principal.contaId(), empresa.getId(), Ambiente.SANDBOX,
                        sim.chaveAcesso(), sim.numeroNfse(), assinado, agora)
                : Emissao.rejeitada(principal.contaId(), empresa.getId(), Ambiente.SANDBOX,
                        sim.motivo(), assinado, agora);
        emissaoRepository.save(emissao);
        return toResponse(emissao);
    }

    @Transactional(readOnly = true)
    public EmissaoResponse consultar(UUID contaId, UUID id) {
        return emissaoRepository.findByIdAndContaId(id, contaId).map(this::toResponse)
                .orElseThrow(() -> new NaoEncontradoException("Emissão não encontrada: " + id));
    }

    private RequisicaoDpsDto montarDto(Empresa e, EmitirNfseRequest req, String numero) {
        String dhEmi = OffsetDateTime.now(ZoneOffset.of("-03:00"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
        String dCompet = OffsetDateTime.now(ZoneOffset.of("-03:00"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return new RequisicaoDpsDto(
                e.getCnpj(), e.getCodMunIbge(), "1", numero, dhEmi, dCompet,
                req.servico().codMunPrestacao(), req.servico().codTribNacional(),
                req.servico().descricao(), req.valores().valorServico(),
                e.getOpSimplesNacional().codigo(), e.getRegimeEspecialTributacao().codigo(),
                req.valores().tributacaoIssqn(), req.valores().tipoRetencaoIssqn());
    }

    private EmissaoResponse toResponse(Emissao e) {
        return new EmissaoResponse(e.getId(), e.getStatus().name(), e.getChaveAcesso(),
                e.getNumeroNfse(), e.getMotivo(), e.getXmlDps());
    }
}
```

- [ ] **Step 4: `EmissaoController`**

```java
package br.com.lc.nfse.api.emissao;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import br.com.lc.nfse.api.emissao.dto.EmissaoResponse;
import br.com.lc.nfse.api.emissao.dto.EmitirNfseRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/nfse")
public class EmissaoController {

    private final EmissaoService service;

    public EmissaoController(EmissaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmissaoResponse> emitir(@AuthenticationPrincipal TenantPrincipal principal,
                                                  @RequestBody EmitirNfseRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.emitir(principal, req));
    }

    @GetMapping("/{id}")
    public EmissaoResponse consultar(@AuthenticationPrincipal TenantPrincipal principal,
                                     @PathVariable UUID id) {
        return service.consultar(principal.contaId(), id);
    }
}
```

> `SecurityConfig`: `/v1/**` já exige `ROLE_TENANT` — cobre `/v1/nfse`. Nada a mudar.

- [ ] **Step 5: Teste `EmissaoSandboxTest`** (estende `AbstractPostgresIT`) — semeia Conta+chave sandbox e uma Empresa com regime (via repos). Casos:
  - emitir (POST /v1/nfse) → 202 com id + status AUTORIZADA; GET /v1/nfse/{id} → AUTORIZADA, chaveAcesso 50 díg., xmlDps contém `<Signature`.
  - `simular=REJEITADA` → GET → REJEITADA + motivo, chaveAcesso null.
  - `codMunPrestacao` inválido ("123") → 422.
  - cross-tenant: Conta B consulta emissão da A → 404.
  - chave de produção (`sk_live_...`) → 501.

  (Escrever este teste ANTES da implementação dos Steps 3-4 — dirige o fluxo.)

- [ ] **Step 6: Rodar** `.\mvnw.cmd -B "-Dtest=EmissaoSandboxTest" test`, depois a suíte completa `.\mvnw.cmd -B test`. Expected: tudo verde.

- [ ] **Step 7: Commit** `feat(2a): POST /v1/nfse (202) + GET /v1/nfse/{id} — emissao sandbox simulada`.

---

## Self-Review

- **Cobertura do spec:** regime na Empresa (§3) → Fase 1; Emissao (§4) → Fase 2; simulador (§8) → Fase 3;
  cert sandbox (§7) → Fase 4; contrato/fluxo/erros (§5,§6,§9) → Fase 5; testes (§10) → Fase 5 Step 5. ✔
- **Placeholders:** Fases com código real; DTOs/entidades repetitivas descritas por interface + exemplo. ✔
- **Consistência de tipos:** `RequisicaoDpsDto` (14 args do #1) montado no `montarDto`; `EmitirNfseRequest`
  aninhado (servico/valores) usado no controller e service; `ResultadoSimulado`/`Emissao.autorizada/rejeitada`
  batendo entre simulador, service e teste. ✔

## Execution Handoff

Plano completo. Requer o `nfse-postgres` (55432) no ar. Executar em ordem; cada fase fecha com teste verde.
