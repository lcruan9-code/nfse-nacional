package br.com.lc.nfse.core.municipal;

import br.com.lc.nfse.core.cert.CertificadoLoader;

/** Strategy: todo provedor municipal implementa este contrato único. */
public interface ProvedorMunicipal {
    TipoProvedor tipo();
    ResultadoEmissao emitir(RpsRequest rps, CertificadoLoader.Certificado cert, ProvedorConfig cfg);
}
