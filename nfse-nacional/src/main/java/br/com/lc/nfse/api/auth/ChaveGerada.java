package br.com.lc.nfse.api.auth;

/** Uma chave recém-gerada: o texto integral (mostrado 1x), o prefixo, o hash e o ambiente. */
public record ChaveGerada(String textoIntegral, String prefixo, String hash, Ambiente ambiente) {}
