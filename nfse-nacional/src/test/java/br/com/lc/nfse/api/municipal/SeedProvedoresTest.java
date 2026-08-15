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
