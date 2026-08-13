package br.com.lc.nfse.web;

/**
 * Resposta do preview: a DPS montada, se validou, se foi assinada, e o pacote
 * (gzip+base64) pronto para transmitir ao ADN quando houver A1.
 */
public record RespostaDpsPreview(
        boolean valido,
        boolean assinado,
        String xml,
        String pacoteGzipB64,
        String mensagem
) {}
