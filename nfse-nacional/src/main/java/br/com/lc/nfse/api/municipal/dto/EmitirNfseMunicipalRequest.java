package br.com.lc.nfse.api.municipal.dto;

import java.util.UUID;

/** Emissão municipal: empresa (prestador+regime) + IBGE de incidência + dados do serviço. */
public record EmitirNfseMunicipalRequest(UUID empresaId, String codigoMunicipioIbge,
                                         Servico servico, String simular) {

    public record Servico(String valorServicos, String itemListaServico, String discriminacao,
                          int issRetido, int exigibilidadeIss) {}
}
