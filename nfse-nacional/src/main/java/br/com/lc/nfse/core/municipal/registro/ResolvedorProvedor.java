package br.com.lc.nfse.core.municipal.registro;

import br.com.lc.nfse.core.municipal.AlgoritmoAssinatura;
import br.com.lc.nfse.core.municipal.EstiloEnvelope;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.TipoProvedor;
import org.springframework.stereotype.Service;

/** Cérebro de roteamento: dado o código IBGE, decide o modo de emissão. */
@Service
public class ResolvedorProvedor {

    private final ProvedorMunicipalRegistroRepository repo;

    public ResolvedorProvedor(ProvedorMunicipalRegistroRepository repo) {
        this.repo = repo;
    }

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
}
