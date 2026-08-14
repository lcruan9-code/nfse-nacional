package br.com.lc.nfse.api.emissao;

public record ResultadoSimulado(StatusEmissao status, String chaveAcesso, String numeroNfse, String motivo) {}
