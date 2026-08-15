package br.com.lc.nfse.core.municipal.registro;

import br.com.lc.nfse.core.municipal.ProvedorConfig;

/** Resultado da resolução por IBGE. */
public sealed interface ResolucaoProvedor
        permits ResolucaoProvedor.ProvedorResolvido, ResolucaoProvedor.CidadeAdn,
                ResolucaoProvedor.CidadeNaoSuportada {

    record ProvedorResolvido(ProvedorConfig config) implements ResolucaoProvedor {}
    record CidadeAdn() implements ResolucaoProvedor {}
    record CidadeNaoSuportada(String ibge, String provedor) implements ResolucaoProvedor {}
}
