package br.com.lc.nfse.api.web.dto;

import java.util.UUID;

/** As chaves em texto aparecem SÓ aqui, uma única vez (depois só o hash fica no banco). */
public record ContaCriadaResponse(UUID contaId, String nome, String chaveSandbox, String chaveProducao) {}
