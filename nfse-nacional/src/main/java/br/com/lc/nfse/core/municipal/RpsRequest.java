package br.com.lc.nfse.core.municipal;

import java.time.LocalDate;

/** Modelo canônico interno da nota — neutro de dialeto. Cada provedor traduz deste modelo. */
public record RpsRequest(
        String numero, String serie, String tipo,
        LocalDate dataEmissao, LocalDate competencia,
        String valorServicos, int issRetido, String itemListaServico, String discriminacao,
        String codigoMunicipioIbge, int exigibilidadeIss,
        String prestadorCnpj, String prestadorInscricaoMunicipal,
        int optanteSimplesNacional, int incentivoFiscal) {}
