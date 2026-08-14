package br.com.lc.nfse.api.web.dto;

import java.util.UUID;

public record EmpresaResponse(
        UUID id,
        String cnpj,
        String razaoSocial,
        String codMunIbge,
        String status
) {}
