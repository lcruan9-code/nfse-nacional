package br.com.lc.nfse.api.error;

import br.com.lc.nfse.api.emissao.ProducaoIndisponivelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converte exceções de domínio no envelope de erro com o status HTTP certo. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<EnvelopeErro> naoEncontrado(NaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(EnvelopeErro.de("nao_encontrado", e.getMessage()));
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<EnvelopeErro> conflito(ConflitoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(EnvelopeErro.de("conflito", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<EnvelopeErro> validacao(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity()
                .body(EnvelopeErro.de("validacao", e.getMessage()));
    }

    @ExceptionHandler(ProducaoIndisponivelException.class)
    public ResponseEntity<EnvelopeErro> producaoIndisponivel(ProducaoIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(EnvelopeErro.de("producao_indisponivel", e.getMessage()));
    }
}
