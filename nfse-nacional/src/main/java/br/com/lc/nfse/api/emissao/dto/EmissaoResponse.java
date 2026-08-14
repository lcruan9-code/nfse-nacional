package br.com.lc.nfse.api.emissao.dto;

import java.util.UUID;

public record EmissaoResponse(UUID id, String status, String chaveAcesso, String numeroNfse,
                              String motivo, String xmlDps,
                              String aliquotaCbs, String valorCbs, String aliquotaIbs, String valorIbs) {}
