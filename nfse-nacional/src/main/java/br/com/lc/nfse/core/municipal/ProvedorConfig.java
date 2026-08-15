package br.com.lc.nfse.core.municipal;

/** Config de emissão por provedor/município, resolvida a partir do IBGE. */
public record ProvedorConfig(
        TipoProvedor tipo, String versaoAbrasf,
        String urlHomolog, String urlProd,
        EstiloEnvelope estiloEnvelope, AlgoritmoAssinatura algoritmo) {}
