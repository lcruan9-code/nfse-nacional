package br.com.lc.nfse.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: um único PostgreSQL do Testcontainers compartilhado por
 * TODAS as classes (padrão singleton — iniciado uma vez no bloco static, nunca parado
 * explicitamente; o Ryuk do Testcontainers limpa no fim da JVM). O Flyway rode de verdade;
 * migrations são idempotentes entre contextos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Banco limpo antes de cada teste — o container é compartilhado por todas as classes. */
    @BeforeEach
    void limparBanco() {
        jdbcTemplate.execute("truncate table emissoes, api_keys, empresas, contas restart identity cascade");
    }
}
