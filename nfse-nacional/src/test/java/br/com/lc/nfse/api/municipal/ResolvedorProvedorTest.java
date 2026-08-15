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
