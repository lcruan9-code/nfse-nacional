# API Comercial NFS-e — Auth + Multi-tenant (Fatia 2b) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fundação de acesso da API de NFS-e — autenticação por `X-Api-Key` e multi-tenant de dois níveis (Conta → Empresas) em PostgreSQL, com isolamento por tenant provado por teste.

**Architecture:** Mesmo app Spring Boot (`nfse-nacional`), pacotes novos em `br.com.lc.nfse.api`. Spring Security com um filtro de API key que resolve Conta + ambiente. JPA + Flyway sobre PostgreSQL 16 (Docker local). Testes de integração com Testcontainers + `RestClient` (HTTP real).

**Tech Stack:** Java 17 (JDK 17), Spring Boot 4.1, Spring Security, Spring Data JPA, PostgreSQL 16, Flyway, Testcontainers, JUnit 5.

## Global Constraints

- **Build no JDK 17**, nunca no Java 8 global. Maven via wrapper (`mvnw.cmd`). Env do build por sessão.
- **Foco exclusivo em NFS-e.** Nada de NF-e/NFC-e nesta fatia.
- **Chave nunca em texto no banco:** só SHA-256 (`key_hash`) + prefixo curto. Texto só 1x na criação.
- **Isolamento por tenant** em toda query de Empresa (escopo por `conta_id`). Cross-tenant → 404.
- **TDD:** teste primeiro nas partes de risco (auth, isolamento); infra (Flyway/boot) valida por smoke.
- **Commits locais apenas.** Sem push.
- **Testes de integração:** `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `RestClient` (spring-web), Testcontainers Postgres via `@ServiceConnection`. Evita o autoconfigure de teste do Boot 4.
- **Docker:** container `nfse-postgres`, volume `nfse-pgdata`, imagem `postgres:16`, db/user `nfse`.
- Raiz do projeto: `C:\PROJETOS\Ruan\NFS-e\nfse-nacional\`. Git em `C:\PROJETOS\Ruan\NFS-e\`.

---

## Roteiro de Fases

| Fase | Entrega |
|------|---------|
| 0 | Postgres no Docker + dependências + config + Flyway V1 + app sobe contra o banco |
| 1 | Entidades + repositórios (Conta, Empresa, ApiKey) + base de teste Testcontainers |
| 2 | Geração de API key (formato `sk_test_/sk_live_`, SHA-256) + testes |
| 3 | Spring Security: filtro de API key + `GET /v1/whoami` + testes (válida/inválida/revogada/ambiente) |
| 4 | Provisionamento admin: `POST /admin/contas` (emite chaves 1x) + testes |
| 5 | `/v1/empresas` (CRUD escopado) + isolamento cross-tenant + envelope de erro + testes |

---

## FASE 0 — Postgres, dependências e boot

### Task 0.1: Subir o PostgreSQL no Docker (container + volume nomeados)

**Files:** nenhum (infra).

- [ ] **Step 1: Criar volume persistente e container**

```powershell
docker volume create nfse-pgdata
docker run --name nfse-postgres -e POSTGRES_USER=nfse -e POSTGRES_PASSWORD=nfse_dev_pwd -e POSTGRES_DB=nfse -p 5432:5432 -v nfse-pgdata:/var/lib/postgresql/data -d postgres:16
```

Expected: container `nfse-postgres` rodando. Verificar:

```powershell
docker ps --filter "name=nfse-postgres"
docker exec nfse-postgres pg_isready -U nfse
```

Expected: `accepting connections`.

### Task 0.2: Dependências no `pom.xml`

**Files:** Modify `nfse-nacional/pom.xml`

- [ ] **Step 1: Adicionar dependências** dentro de `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Baixar e compilar**

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -B -DskipTests compile
```

Expected: BUILD SUCCESS (dependências resolvidas).

### Task 0.3: Config de datasource + Flyway V1 + boot verde

**Files:**
- Modify `nfse-nacional/src/main/resources/application-homolog.yml`
- Create `nfse-nacional/src/main/resources/db/migration/V1__contas_empresas_apikeys.sql`

- [ ] **Step 1: Datasource/JPA/Flyway no `application-homolog.yml`** (acrescentar ao conteúdo existente):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nfse
    username: nfse
    password: ${NFSE_DB_PASSWORD:nfse_dev_pwd}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
nfse:
  admin:
    api-key: ${NFSE_ADMIN_KEY:admin_dev_key}
```

- [ ] **Step 2: Migration `V1__contas_empresas_apikeys.sql`**

```sql
create table contas (
    id            uuid primary key,
    nome          varchar(200) not null,
    status        varchar(20)  not null default 'ATIVA',
    criado_em     timestamptz  not null default now(),
    atualizado_em timestamptz  not null default now()
);

create table empresas (
    id                  uuid primary key,
    conta_id            uuid not null references contas(id),
    cnpj                varchar(14) not null,
    razao_social        varchar(200) not null,
    inscricao_municipal varchar(30),
    cod_mun_ibge        varchar(7) not null,
    status              varchar(20) not null default 'ATIVA',
    criado_em           timestamptz not null default now(),
    atualizado_em       timestamptz not null default now(),
    constraint uq_empresa_conta_cnpj unique (conta_id, cnpj)
);
create index ix_empresas_conta on empresas(conta_id);

create table api_keys (
    id           uuid primary key,
    conta_id     uuid not null references contas(id),
    ambiente     varchar(10) not null,
    key_hash     varchar(64) not null,
    key_prefix   varchar(20) not null,
    status       varchar(20) not null default 'ATIVA',
    criado_em    timestamptz not null default now(),
    ultimo_uso_em timestamptz,
    constraint uq_api_key_hash unique (key_hash)
);
create index ix_api_keys_conta on api_keys(conta_id);
```

- [ ] **Step 3: Subir o app contra o Postgres (smoke manual)**

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -B spring-boot:run
```

Expected: log do Flyway aplicando `V1`, e `Started NfseNacionalApplication`. Parar com Ctrl+C (ou matar processo). Confirmar tabelas:

```powershell
docker exec nfse-postgres psql -U nfse -d nfse -c "\dt"
```

Expected: `contas`, `empresas`, `api_keys`, `flyway_schema_history`.

- [ ] **Step 4: Commit**

```powershell
git -C C:\PROJETOS\Ruan\NFS-e add nfse-nacional/pom.xml nfse-nacional/src/main/resources
git -C C:\PROJETOS\Ruan\NFS-e commit -m "chore: postgres+jpa+flyway; migration V1 (contas, empresas, api_keys)"
```

---

## FASE 1 — Entidades, repositórios e base de teste

### Task 1.1: Enums e entidades JPA

**Files:**
- Create `api/tenant/Conta.java`, `api/tenant/Empresa.java`, `api/auth/ApiKey.java`
- Create `api/tenant/StatusConta.java`, `api/tenant/StatusEmpresa.java`, `api/auth/Ambiente.java`, `api/auth/StatusApiKey.java`

**Interfaces:**
- Produces: entidades com `UUID id` gerado na aplicação (`UUID.randomUUID()` no construtor de criação).

- [ ] **Step 1: Enums** (`br.com.lc.nfse.api.auth.Ambiente`):

```java
package br.com.lc.nfse.api.auth;

public enum Ambiente { SANDBOX, PRODUCAO }
```

`StatusApiKey` (`ATIVA`, `REVOGADA`), `StatusConta` (`ATIVA`, `SUSPENSA`), `StatusEmpresa` (`ATIVA`, `INATIVA`) — cada um em seu arquivo, mesmo padrão.

- [ ] **Step 2: Entidade `Conta`** (`br.com.lc.nfse.api.tenant.Conta`):

```java
package br.com.lc.nfse.api.tenant;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contas")
public class Conta {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConta status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Conta() {}

    public static Conta nova(String nome, OffsetDateTime agora) {
        Conta c = new Conta();
        c.id = UUID.randomUUID();
        c.nome = nome;
        c.status = StatusConta.ATIVA;
        c.criadoEm = agora;
        c.atualizadoEm = agora;
        return c;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public StatusConta getStatus() { return status; }
}
```

- [ ] **Step 3: Entidade `Empresa`** (mesmo padrão), campos: `id`, `@Column(name="conta_id") UUID contaId`, `cnpj`, `razaoSocial` (`@Column(name="razao_social")`), `inscricaoMunicipal` (nullable), `codMunIbge` (`@Column(name="cod_mun_ibge")`), `status` (StatusEmpresa), `criadoEm`, `atualizadoEm`. Factory `Empresa.nova(UUID contaId, String cnpj, String razaoSocial, String inscricaoMunicipal, String codMunIbge, OffsetDateTime agora)`.

- [ ] **Step 4: Entidade `ApiKey`** (`br.com.lc.nfse.api.auth.ApiKey`), campos: `id`, `@Column(name="conta_id") UUID contaId`, `ambiente` (enum STRING), `keyHash` (`@Column(name="key_hash")`), `keyPrefix` (`@Column(name="key_prefix")`), `status` (StatusApiKey), `criadoEm`, `ultimoUsoEm` (nullable). Factory `ApiKey.nova(UUID contaId, Ambiente ambiente, String keyHash, String keyPrefix, OffsetDateTime agora)`. Método `void revogar()` e `void marcarUso(OffsetDateTime)`.

### Task 1.2: Repositórios

**Files:** Create `api/tenant/ContaRepository.java`, `api/tenant/EmpresaRepository.java`, `api/auth/ApiKeyRepository.java`

**Interfaces:**
- Produces:
  - `ContaRepository extends JpaRepository<Conta, UUID>`
  - `EmpresaRepository extends JpaRepository<Empresa, UUID>` com
    `List<Empresa> findByContaId(UUID contaId)`,
    `Optional<Empresa> findByIdAndContaId(UUID id, UUID contaId)`,
    `boolean existsByContaIdAndCnpj(UUID contaId, String cnpj)`
  - `ApiKeyRepository extends JpaRepository<ApiKey, UUID>` com
    `Optional<ApiKey> findByKeyHashAndStatus(String keyHash, StatusApiKey status)`

- [ ] **Step 1: Escrever os três repositórios** (interfaces Spring Data, sem corpo).

### Task 1.3: Base de teste Testcontainers

**Files:** Create `src/test/java/br/com/lc/nfse/api/AbstractPostgresIT.java`

- [ ] **Step 1: Classe base**

```java
package br.com.lc.nfse.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
}
```

- [ ] **Step 2: Teste de repositório** `EmpresaRepositoryIT` (estende `AbstractPostgresIT`, `@Autowired` repos): cria Conta, cria 2 Empresas nela + 1 Empresa em outra Conta; `findByContaId` retorna 2; `findByIdAndContaId` com conta errada retorna vazio; `existsByContaIdAndCnpj` true/false. Rodar:

```powershell
.\mvnw.cmd -B "-Dtest=EmpresaRepositoryIT" test
```

Expected: PASS (prova entidades + mapeamento + isolamento no nível repo).

- [ ] **Step 3: Commit** `feat: entidades e repositorios (Conta, Empresa, ApiKey) + base Testcontainers`.

---

## FASE 2 — Geração de API key

### Task 2.1: Serviço de geração/hash de chave

**Files:** Create `api/auth/ApiKeyService.java`, `api/auth/ChaveGerada.java`; Test `src/test/java/br/com/lc/nfse/api/auth/ApiKeyServiceTest.java`

**Interfaces:**
- Produces:
  - `record ChaveGerada(String textoIntegral, String prefixo, String hash, Ambiente ambiente)`
  - `ApiKeyService.gerar(Ambiente) : ChaveGerada`
  - `ApiKeyService.hash(String chave) : String` (SHA-256 hex)
  - `ApiKeyService.ambienteDe(String chave) : Optional<Ambiente>` (pelo prefixo)

- [ ] **Step 1: Teste falhando** `ApiKeyServiceTest`:

```java
package br.com.lc.nfse.api.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyServiceTest {

    private final ApiKeyService svc = new ApiKeyService();

    @Test
    void gerarSandboxTemPrefixoEHashConsistente() {
        ChaveGerada c = svc.gerar(Ambiente.SANDBOX);
        assertTrue(c.textoIntegral().startsWith("sk_test_"));
        assertEquals(Ambiente.SANDBOX, c.ambiente());
        assertEquals(svc.hash(c.textoIntegral()), c.hash());
        assertEquals(64, c.hash().length()); // SHA-256 hex
        assertTrue(c.textoIntegral().startsWith(c.prefixo()));
    }

    @Test
    void gerarProducaoUsaPrefixoLive() {
        assertTrue(svc.gerar(Ambiente.PRODUCAO).textoIntegral().startsWith("sk_live_"));
    }

    @Test
    void ambienteDeReconhecePeloPrefixo() {
        assertEquals(Ambiente.SANDBOX, svc.ambienteDe("sk_test_abc").orElseThrow());
        assertEquals(Ambiente.PRODUCAO, svc.ambienteDe("sk_live_abc").orElseThrow());
        assertTrue(svc.ambienteDe("xyz").isEmpty());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar** (`-Dtest=ApiKeyServiceTest`). Expected: não compila / falha.

- [ ] **Step 3: Implementar `ChaveGerada` e `ApiKeyService`**

```java
package br.com.lc.nfse.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

public class ApiKeyService {

    private static final String PREFIXO_SANDBOX = "sk_test_";
    private static final String PREFIXO_PRODUCAO = "sk_live_";
    private static final int BYTES_SEGREDO = 32;

    private final SecureRandom random = new SecureRandom();

    public ChaveGerada gerar(Ambiente ambiente) {
        String prefixoAmb = ambiente == Ambiente.SANDBOX ? PREFIXO_SANDBOX : PREFIXO_PRODUCAO;
        byte[] segredo = new byte[BYTES_SEGREDO];
        random.nextBytes(segredo);
        String texto = prefixoAmb + HexFormat.of().formatHex(segredo);
        String prefixoExibicao = texto.substring(0, Math.min(14, texto.length()));
        return new ChaveGerada(texto, prefixoExibicao, hash(texto), ambiente);
    }

    public String hash(String chave) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256")
                    .digest(chave.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public Optional<Ambiente> ambienteDe(String chave) {
        if (chave == null) return Optional.empty();
        if (chave.startsWith(PREFIXO_SANDBOX)) return Optional.of(Ambiente.SANDBOX);
        if (chave.startsWith(PREFIXO_PRODUCAO)) return Optional.of(Ambiente.PRODUCAO);
        return Optional.empty();
    }
}
```

```java
package br.com.lc.nfse.api.auth;

public record ChaveGerada(String textoIntegral, String prefixo, String hash, Ambiente ambiente) {}
```

- [ ] **Step 4: Rodar e ver passar.** Commit `feat: ApiKeyService (formato sk_test_/sk_live_, SHA-256)`.

---

## FASE 3 — Segurança: filtro de API key + whoami

### Task 3.1: TenantPrincipal + filtro + config + endpoint whoami

**Files:**
- Create `api/auth/TenantPrincipal.java`, `api/auth/ApiKeyAuthFilter.java`, `api/auth/SecurityConfig.java`, `api/auth/EnvelopeErroAuthEntryPoint.java`
- Create `api/web/WhoamiController.java`
- Test `src/test/java/br/com/lc/nfse/api/auth/AuthWhoamiIT.java`

**Interfaces:**
- Consumes: `ApiKeyService`, `ApiKeyRepository`, `ContaRepository`.
- Produces: `record TenantPrincipal(UUID contaId, String nomeConta, Ambiente ambiente)`; requests a `/v1/**` autenticadas expõem o principal via `SecurityContextHolder`.

- [ ] **Step 1: `TenantPrincipal`**

```java
package br.com.lc.nfse.api.auth;

import java.util.UUID;

public record TenantPrincipal(UUID contaId, String nomeConta, Ambiente ambiente) {}
```

- [ ] **Step 2: `ApiKeyAuthFilter`**

```java
package br.com.lc.nfse.api.auth;

import br.com.lc.nfse.api.tenant.Conta;
import br.com.lc.nfse.api.tenant.ContaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;
    private final ContaRepository contaRepository;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ApiKeyRepository apiKeyRepository,
                            ContaRepository contaRepository) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
        this.contaRepository = contaRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String chave = req.getHeader("X-Api-Key");
        if (chave != null && !chave.isBlank()) {
            resolver(chave).ifPresent(principal -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_TENANT")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }

    private Optional<TenantPrincipal> resolver(String chave) {
        return apiKeyService.ambienteDe(chave).flatMap(amb -> apiKeyRepository
                .findByKeyHashAndStatus(apiKeyService.hash(chave), StatusApiKey.ATIVA)
                .filter(k -> k.getAmbiente() == amb)
                .flatMap(k -> contaRepository.findById(k.getContaId()))
                .map(c -> new TenantPrincipal(c.getId(), c.getNome(), amb)));
    }
}
```

> Nota: `ApiKey` precisa expor `getAmbiente()`, `getContaId()`. `Conta` já expõe `getId()/getNome()`.

- [ ] **Step 3: `EnvelopeErroAuthEntryPoint`** (401 em JSON):

```java
package br.com.lc.nfse.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class EnvelopeErroAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e)
            throws IOException {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"erro\":{\"codigo\":\"nao_autenticado\",\"mensagem\":\"X-Api-Key ausente ou inválida\"}}");
    }
}
```

- [ ] **Step 4: `SecurityConfig`**

```java
package br.com.lc.nfse.api.auth;

import br.com.lc.nfse.api.tenant.ContaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyService s, ApiKeyRepository r, ContaRepository c) {
        return new ApiKeyAuthFilter(s, r, c);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/dps/preview", "/admin/**").permitAll()
                .requestMatchers("/v1/**").hasRole("TENANT")
                .anyRequest().permitAll())
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new EnvelopeErroAuthEntryPoint()));
        return http.build();
    }
}
```

> `/dps/preview` e `/admin/**` ficam `permitAll` no nível do Security (o admin faz a checagem própria da `X-Admin-Key` na Fase 4).

- [ ] **Step 5: `WhoamiController`**

```java
package br.com.lc.nfse.api.web;

import br.com.lc.nfse.api.auth.TenantPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WhoamiController {

    @GetMapping("/v1/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal TenantPrincipal principal) {
        return Map.of(
                "contaId", principal.contaId(),
                "nomeConta", principal.nomeConta(),
                "ambiente", principal.ambiente());
    }
}
```

- [ ] **Step 6: Teste de integração `AuthWhoamiIT`** (estende `AbstractPostgresIT`): injeta `@LocalServerPort int port`, `@Autowired` repos + `ApiKeyService`. Semeia uma Conta + uma ApiKey (hash de uma chave sandbox conhecida) direto pelos repositórios. Usa `RestClient`:

```java
package br.com.lc.nfse.api.auth;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.api.tenant.Conta;
import br.com.lc.nfse.api.tenant.ContaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWhoamiIT extends AbstractPostgresIT {

    @LocalServerPort int port;
    @Autowired ContaRepository contaRepository;
    @Autowired ApiKeyRepository apiKeyRepository;
    @Autowired ApiKeyService apiKeyService;

    private RestClient client() { return RestClient.create("http://localhost:" + port); }

    @Test
    void chaveValidaRetornaWhoami() {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta conta = contaRepository.save(Conta.nova("Cliente Teste", agora));
        String chave = "sk_test_deadbeefdeadbeefdeadbeefdeadbeef";
        apiKeyRepository.save(ApiKey.nova(conta.getId(), Ambiente.SANDBOX,
                apiKeyService.hash(chave), chave.substring(0, 14), agora));

        var resp = client().get().uri("/v1/whoami").header("X-Api-Key", chave)
                .retrieve().toEntity(String.class);

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("SANDBOX"));
        assertTrue(resp.getBody().contains("Cliente Teste"));
    }

    @Test
    void semChaveRetorna401() {
        HttpStatusCode status = client().get().uri("/v1/whoami")
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(401, status.value());
    }

    @Test
    void chaveInexistenteRetorna401() {
        HttpStatusCode status = client().get().uri("/v1/whoami")
                .header("X-Api-Key", "sk_test_naoexiste")
                .exchange((req, res) -> res.getStatusCode());
        assertEquals(401, status.value());
    }
}
```

- [ ] **Step 7: Rodar `-Dtest=AuthWhoamiIT`.** Expected: PASS. Commit `feat: auth por X-Api-Key (Spring Security) + GET /v1/whoami`.

---

## FASE 4 — Provisionamento admin

### Task 4.1: `POST /admin/contas`

**Files:**
- Create `api/web/AdminContaController.java`, `api/web/dto/CriarContaRequest.java`, `api/web/dto/ContaCriadaResponse.java`
- Create `api/tenant/ProvisionamentoService.java`
- Test `src/test/java/br/com/lc/nfse/api/web/AdminContaIT.java`

**Interfaces:**
- Consumes: `ContaRepository`, `ApiKeyRepository`, `ApiKeyService`.
- Produces: cria Conta + 2 chaves (sandbox+prod), persiste os hashes, retorna as chaves em texto 1x.

- [ ] **Step 1: DTOs**

```java
package br.com.lc.nfse.api.web.dto;
public record CriarContaRequest(String nome) {}
```

```java
package br.com.lc.nfse.api.web.dto;
import java.util.UUID;
public record ContaCriadaResponse(UUID contaId, String nome, String chaveSandbox, String chaveProducao) {}
```

- [ ] **Step 2: `ProvisionamentoService`** — método `ContaCriadaResponse criarConta(String nome)`: cria e salva `Conta`; para cada ambiente gera `ChaveGerada`, salva `ApiKey.nova(...)` com o hash/prefixo, e coleta o texto integral; retorna o response com as duas chaves em texto. Usa `OffsetDateTime.now()`. Anotar `@Service` e `@Transactional`.

- [ ] **Step 3: `AdminContaController`** — `@PostMapping("/admin/contas")`; injeta `ProvisionamentoService` e `@Value("${nfse.admin.api-key}") String adminKey`. Lê header `X-Admin-Key`; compara em tempo constante (`MessageDigest.isEqual` sobre bytes UTF-8); se não bater → 401 com envelope. Se bater → chama o service e retorna 201 com `ContaCriadaResponse`.

- [ ] **Step 4: Teste `AdminContaIT`** (estende `AbstractPostgresIT`, usa a `nfse.admin.api-key` do profile de teste — semear via `@DynamicPropertySource` ou usar o default `admin_dev_key`): 
  - `X-Admin-Key` correta → 201, corpo tem `chaveSandbox` começando com `sk_test_` e `chaveProducao` com `sk_live_`; confirmar que 2 `api_keys` foram persistidas para a conta.
  - `X-Admin-Key` errada → 401.

- [ ] **Step 5: Rodar, ver passar. Commit** `feat: POST /admin/contas provisiona conta + emite chaves`.

---

## FASE 5 — Empresas (CRUD escopado) + isolamento

### Task 5.1: Envelope de erro + exceções

**Files:** Create `api/error/EnvelopeErro.java`, `api/error/NaoEncontradoException.java`, `api/error/ConflitoException.java`, `api/error/ApiExceptionHandler.java`

- [ ] **Step 1: `EnvelopeErro`**

```java
package br.com.lc.nfse.api.error;
public record EnvelopeErro(Erro erro) {
    public record Erro(String codigo, String mensagem) {}
    public static EnvelopeErro de(String codigo, String mensagem) {
        return new EnvelopeErro(new Erro(codigo, mensagem));
    }
}
```

- [ ] **Step 2: Exceções** `NaoEncontradoException` e `ConflitoException` (RuntimeException com mensagem).

- [ ] **Step 3: `ApiExceptionHandler`** (`@RestControllerAdvice`): mapeia `NaoEncontradoException`→404, `ConflitoException`→409, `MethodArgumentNotValidException`/`IllegalArgumentException`→422, cada um retornando `EnvelopeErro.de(...)` com `ResponseEntity` do status certo.

### Task 5.2: Endpoints de Empresa

**Files:**
- Create `api/tenant/EmpresaService.java`, `api/web/EmpresaController.java`, `api/web/dto/CriarEmpresaRequest.java`, `api/web/dto/EmpresaResponse.java`
- Test `src/test/java/br/com/lc/nfse/api/web/EmpresaIT.java`

**Interfaces:**
- Consumes: `EmpresaRepository`, `TenantPrincipal` (via `@AuthenticationPrincipal`).
- Produces: `EmpresaService` com `criar(UUID contaId, CriarEmpresaRequest)`, `listar(UUID contaId)`, `buscar(UUID contaId, UUID id)` (lança `NaoEncontradoException` se de outra conta).

- [ ] **Step 1: DTOs** `CriarEmpresaRequest(String cnpj, String razaoSocial, String inscricaoMunicipal, String codMunIbge)` e `EmpresaResponse(UUID id, String cnpj, String razaoSocial, String codMunIbge, String status)`.

- [ ] **Step 2: Teste de integração `EmpresaIT`** (estende `AbstractPostgresIT`) — semeia 2 Contas (A e B), cada uma com uma chave sandbox conhecida. Via `RestClient`:
  - `POST /v1/empresas` (chave A, CNPJ válido) → 201; corpo tem o id.
  - `GET /v1/empresas` (chave A) → contém a empresa criada; (chave B) → não contém.
  - `GET /v1/empresas/{idDaA}` com **chave B** → **404** (isolamento).
  - `POST /v1/empresas` com o mesmo CNPJ 2x na conta A → 409.
  - `POST /v1/empresas` com `codMunIbge` inválido (ex.: "123") → 422.

  (Escrever este teste ANTES da implementação — é o teste de risco que dirige o isolamento.)

- [ ] **Step 3: Rodar e ver falhar.**

- [ ] **Step 4: Implementar `EmpresaService`** (validação de formato de CNPJ 14 díg. e IBGE 7 díg. → `IllegalArgumentException`; `existsByContaIdAndCnpj` → `ConflitoException`; `findByIdAndContaId` vazio → `NaoEncontradoException`) e `EmpresaController` (`@PostMapping`/`@GetMapping` sob `/v1/empresas`, `@AuthenticationPrincipal TenantPrincipal` para obter `contaId`).

- [ ] **Step 5: Rodar e ver passar.** Rodar a suíte completa `.\mvnw.cmd -B test`. Expected: tudo verde (incl. os testes do #1).

- [ ] **Step 6: Commit** `feat: /v1/empresas CRUD escopado por tenant + isolamento + envelope de erro`.

---

## Self-Review

- **Cobertura do spec:** tenancy Conta→Empresas (§3/§4) → Fases 1/5; X-Api-Key + SHA-256 (§4/§5) → Fases 2/3; Spring Security (§6) → Fase 3; isolamento (§9) → Fase 5; admin provisioning (§7) → Fase 4; Postgres+Flyway+Docker (§10/§13) → Fase 0; erros (§11) → Fase 5; Testcontainers (§12) → Fases 1–5. ✔
- **Placeholders:** as Fases com código de risco (2, 3, 5) trazem código real; entidades/controllers repetitivos (Empresa, DTOs, handler) descritos por interface exata + padrão do vizinho mostrado. ✔
- **Consistência de tipos:** `TenantPrincipal(contaId, nomeConta, ambiente)` usado igual no filtro, whoami e Empresa; `findByIdAndContaId` e `findByContaId` idênticos entre repo, service e testes; `ChaveGerada`/`ApiKey.nova` consistentes. ✔

## Execution Handoff

Plano completo. Fase 0 exige o `nfse-postgres` no Docker (Task 0.1). Executar em ordem; cada fase fecha com teste verde.
