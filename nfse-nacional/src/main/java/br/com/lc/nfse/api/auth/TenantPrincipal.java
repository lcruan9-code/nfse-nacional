package br.com.lc.nfse.api.auth;

import java.util.UUID;

/** Identidade resolvida a partir da API key: a Conta e o ambiente da requisição. */
public record TenantPrincipal(UUID contaId, String nomeConta, Ambiente ambiente) {}
