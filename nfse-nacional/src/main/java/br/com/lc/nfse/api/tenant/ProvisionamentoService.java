package br.com.lc.nfse.api.tenant;

import br.com.lc.nfse.api.auth.ApiKey;
import br.com.lc.nfse.api.auth.ApiKeyRepository;
import br.com.lc.nfse.api.auth.ApiKeyService;
import br.com.lc.nfse.api.auth.Ambiente;
import br.com.lc.nfse.api.auth.ChaveGerada;
import br.com.lc.nfse.api.web.dto.ContaCriadaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cria uma Conta e emite um par de chaves (sandbox + produção), persistindo só os hashes. */
@Service
public class ProvisionamentoService {

    private final ContaRepository contaRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;

    public ProvisionamentoService(ContaRepository contaRepository, ApiKeyRepository apiKeyRepository,
                                  ApiKeyService apiKeyService) {
        this.contaRepository = contaRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyService = apiKeyService;
    }

    @Transactional
    public ContaCriadaResponse criarConta(String nome) {
        OffsetDateTime agora = OffsetDateTime.now();
        Conta conta = contaRepository.save(Conta.nova(nome, agora));
        ChaveGerada sandbox = emitir(conta.getId(), Ambiente.SANDBOX, agora);
        ChaveGerada producao = emitir(conta.getId(), Ambiente.PRODUCAO, agora);
        return new ContaCriadaResponse(conta.getId(), conta.getNome(),
                sandbox.textoIntegral(), producao.textoIntegral());
    }

    private ChaveGerada emitir(UUID contaId, Ambiente ambiente, OffsetDateTime agora) {
        ChaveGerada chave = apiKeyService.gerar(ambiente);
        apiKeyRepository.save(ApiKey.nova(contaId, ambiente, chave.hash(), chave.prefixo(), agora));
        return chave;
    }
}
