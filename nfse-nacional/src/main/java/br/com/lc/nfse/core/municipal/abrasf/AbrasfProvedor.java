package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.municipal.ProvedorConfig;
import br.com.lc.nfse.core.municipal.ProvedorMunicipal;
import br.com.lc.nfse.core.municipal.ResultadoEmissao;
import br.com.lc.nfse.core.municipal.RpsRequest;
import br.com.lc.nfse.core.municipal.TipoProvedor;

/** Adaptador ABRASF 2.x: monta -> valida -> assina -> envelopa -> [lab] simula -> parseia. */
public class AbrasfProvedor implements ProvedorMunicipal {

    private final AbrasfXmlBuilder builder = new AbrasfXmlBuilder();
    private final AbrasfValidator validator = new AbrasfValidator();
    private final AbrasfSigner signer = new AbrasfSigner();
    private final AbrasfSoapEnvelope envelope = new AbrasfSoapEnvelope();
    private final SimuladorAbrasf simulador = new SimuladorAbrasf();
    private final AbrasfRetornoParser parser = new AbrasfRetornoParser();

    @Override
    public TipoProvedor tipo() { return TipoProvedor.ABRASF_2X; }

    @Override
    public ResultadoEmissao emitir(RpsRequest rps, CertificadoLoader.Certificado cert, ProvedorConfig cfg) {
        return emitir(rps, cert, cfg, "AUTORIZADA");
    }

    public ResultadoEmissao emitir(RpsRequest rps, CertificadoLoader.Certificado cert,
                                   ProvedorConfig cfg, String simular) {
        String id = "rps" + rps.numero();
        String xml = builder.montar(rps, cfg.versaoAbrasf(), id);
        var validacao = validator.validar(xml);
        if (!validacao.valido()) {
            throw new IllegalArgumentException("RPS ABRASF inválido: " + validacao.mensagem());
        }
        String assinado = signer.assinar(xml, cert, cfg.algoritmo());
        envelope.envelopar(assinado, cfg.estiloEnvelope()); // montado (lab não transmite)
        String resposta = simulador.responder(simular, rps.numero(), assinado);
        return parser.parse(assinado, resposta);
    }
}
