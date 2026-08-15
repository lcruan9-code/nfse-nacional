package br.com.lc.nfse.core.municipal;

import br.com.lc.nfse.core.municipal.abrasf.AbrasfProvedor;
import org.springframework.stereotype.Component;

/** Factory: resolve o adaptador a partir do tipo. Nesta fatia só ABRASF_2X. */
@Component
public class FabricaProvedor {

    public ProvedorMunicipal criar(TipoProvedor tipo) {
        return switch (tipo) {
            case ABRASF_2X -> new AbrasfProvedor();
            case ADN -> throw new IllegalArgumentException(
                    "Cidade é Padrão Nacional (ADN); use POST /v1/nfse");
        };
    }
}
