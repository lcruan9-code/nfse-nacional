package br.com.lc.nfse.api.emissao.dto;

import java.util.UUID;

/** Dados de emissão: a Empresa (por id) fornece prestador+regime; o resto vem daqui. */
public record EmitirNfseRequest(UUID empresaId, Servico servico, Valores valores, String simular,
                                String aliquotaCbs, String aliquotaIbs) {

    public record Servico(String codTribNacional, String descricao, String codMunPrestacao) {}

    public record Valores(String valorServico, int tributacaoIssqn, int tipoRetencaoIssqn) {}
}
