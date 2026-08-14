package br.com.lc.nfse.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** Responde 401 no envelope de erro padrão quando falta/invalida a autenticação. */
public class EnvelopeErroAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e)
            throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(
                "{\"erro\":{\"codigo\":\"nao_autenticado\",\"mensagem\":\"X-Api-Key ausente ou inválida\"}}");
    }
}
