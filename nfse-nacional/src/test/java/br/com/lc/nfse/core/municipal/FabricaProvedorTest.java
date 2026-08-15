package br.com.lc.nfse.core.municipal;

import br.com.lc.nfse.core.municipal.abrasf.AbrasfProvedor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FabricaProvedorTest {

    private final FabricaProvedor fabrica = new FabricaProvedor();

    @Test
    void criaAbrasfProvedorParaAbrasf2x() {
        ProvedorMunicipal provedor = fabrica.criar(TipoProvedor.ABRASF_2X);

        assertThat(provedor).isInstanceOf(AbrasfProvedor.class);
        assertThat(provedor.tipo()).isEqualTo(TipoProvedor.ABRASF_2X);
    }

    @Test
    void lancaExcecaoParaAdn() {
        assertThatThrownBy(() -> fabrica.criar(TipoProvedor.ADN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Padrão Nacional");
    }
}
