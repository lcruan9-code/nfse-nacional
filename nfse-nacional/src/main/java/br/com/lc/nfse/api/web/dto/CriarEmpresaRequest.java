package br.com.lc.nfse.api.web.dto;

import br.com.lc.nfse.api.tenant.OpSimplesNacional;
import br.com.lc.nfse.api.tenant.RegimeApuracaoSimplesNacional;
import br.com.lc.nfse.api.tenant.RegimeEspecialTributacao;

public record CriarEmpresaRequest(
        String cnpj,
        String razaoSocial,
        String inscricaoMunicipal,
        String codMunIbge,
        OpSimplesNacional opSimplesNacional,
        RegimeEspecialTributacao regimeEspecialTributacao,
        RegimeApuracaoSimplesNacional regimeApuracaoSimplesNacional
) {}
