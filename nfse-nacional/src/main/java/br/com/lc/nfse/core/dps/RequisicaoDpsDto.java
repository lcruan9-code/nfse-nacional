package br.com.lc.nfse.core.dps;

/**
 * Entrada mínima para montar uma DPS válida no protótipo. Campos de protocolo fixos
 * (tpAmb=2, tpEmit=1, verAplic, versao) são preenchidos pelo {@link DpsBuilder}.
 * Contrato provisório — o contrato comercial definitivo é escopo do subsistema #2.
 */
public record RequisicaoDpsDto(
        String cnpjPrestador,       // 14 dígitos
        String codMunEmissor,       // código IBGE (7 dígitos)
        String serie,               // série da DPS (até 5)
        String numero,              // número da DPS (até 15)
        String dhEmiUtc,            // AAAA-MM-DDThh:mm:ss-03:00
        String dataCompetencia,     // conforme TSData
        String codMunPrestacao,     // código IBGE (7 dígitos)
        String codTribNacional,     // 6 dígitos (LC 116)
        String descricaoServico,    // texto livre
        String valorServico,        // ex.: "100.00"
        int opSimplesNacional,      // 1..3
        int regimeEspecial,         // 0..9
        int tributacaoIssqn,        // 1..4
        int tipoRetencaoIssqn       // 1..3
) {}
