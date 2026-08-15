package br.com.lc.nfse.api.municipal;

import br.com.lc.nfse.api.AbstractPostgresIT;
import br.com.lc.nfse.core.municipal.TipoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolucaoProvedor;
import br.com.lc.nfse.core.municipal.registro.ResolvedorProvedor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedorProvedorTest extends AbstractPostgresIT {

    @Autowired ResolvedorProvedor resolvedor;

    @Test
    void ibgeAbrasf_resolveComConfig() {
        ResolucaoProvedor r = resolvedor.resolver("4204608");
        assertThat(r).isInstanceOf(ResolucaoProvedor.ProvedorResolvido.class);
        var pr = (ResolucaoProvedor.ProvedorResolvido) r;
        assertThat(pr.config().tipo()).isEqualTo(TipoProvedor.ABRASF_2X);
        assertThat(pr.config().versaoAbrasf()).isEqualTo("2.04");
    }

    @Test
    void ibgeAdn_deflete() {
        assertThat(resolvedor.resolver("1501808")).isInstanceOf(ResolucaoProvedor.CidadeAdn.class);
    }

    @Test
    void ibgeDesconhecido_naoSuportado() {
        assertThat(resolvedor.resolver("3550308")).isInstanceOf(ResolucaoProvedor.CidadeNaoSuportada.class);
    }
}
