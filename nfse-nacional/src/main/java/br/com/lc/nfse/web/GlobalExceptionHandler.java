package br.com.lc.nfse.web;

import br.com.lc.nfse.core.cert.CertificadoException;
import br.com.lc.nfse.core.dps.AssinaturaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converte falhas de certificado/assinatura em respostas HTTP 500 com mensagem legível. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CertificadoException.class, AssinaturaException.class})
    public ResponseEntity<RespostaDpsPreview> tratarFalhaInterna(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RespostaDpsPreview(false, false, null, null, e.getMessage()));
    }
}
