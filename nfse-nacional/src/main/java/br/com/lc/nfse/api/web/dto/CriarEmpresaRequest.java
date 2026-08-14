package br.com.lc.nfse.api.web.dto;

public record CriarEmpresaRequest(
        String cnpj,
        String razaoSocial,
        String inscricaoMunicipal,
        String codMunIbge
) {}
