package br.com.lc.nfse.api.auth;

import br.com.lc.nfse.api.tenant.ContaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Resolve a {@code X-Api-Key} em um {@link TenantPrincipal} e popula o SecurityContext. */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;
    private final ContaRepository contaRepository;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ApiKeyRepository apiKeyRepository,
                            ContaRepository contaRepository) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
        this.contaRepository = contaRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String chave = req.getHeader("X-Api-Key");
        if (chave != null && !chave.isBlank()) {
            resolver(chave).ifPresent(principal -> {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_TENANT")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }

    private Optional<TenantPrincipal> resolver(String chave) {
        return apiKeyService.ambienteDe(chave).flatMap(ambiente -> apiKeyRepository
                .findByKeyHashAndStatus(apiKeyService.hash(chave), StatusApiKey.ATIVA)
                .filter(k -> k.getAmbiente() == ambiente)
                .flatMap(k -> contaRepository.findById(k.getContaId()))
                .map(conta -> new TenantPrincipal(conta.getId(), conta.getNome(), ambiente)));
    }
}
