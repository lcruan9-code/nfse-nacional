package br.com.lc.nfse.tela;

/** Dados da empresa (prestador) lidos do MySQL do LC ERP. */
public record EmpresaInfo(
        String cnpjFormatado,
        String cnpjNumerico,
        String razaoSocial,
        String fantasia,
        String inscricaoMunicipal,
        String crt,
        String regime,
        String ibge,
        String cidade
) {}
