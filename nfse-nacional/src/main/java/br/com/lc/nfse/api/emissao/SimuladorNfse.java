package br.com.lc.nfse.api.emissao;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/** Decide o resultado da emissão no sandbox (sem transmitir nada ao ADN). */
@Service
public class SimuladorNfse {

    private final SecureRandom random = new SecureRandom();

    public ResultadoSimulado simular(String simular, String numeroNfse) {
        String pedido = (simular == null || simular.isBlank()) ? "AUTORIZADA" : simular.trim().toUpperCase();
        return switch (pedido) {
            case "AUTORIZADA" ->
                    new ResultadoSimulado(StatusEmissao.AUTORIZADA, chaveFake(), numeroNfse, null);
            case "REJEITADA" ->
                    new ResultadoSimulado(StatusEmissao.REJEITADA, null, null,
                            "Rejeição simulada no ambiente de sandbox (E9999)");
            default -> throw new IllegalArgumentException(
                    "simular inválido: " + simular + " (use AUTORIZADA ou REJEITADA)");
        };
    }

    private String chaveFake() {
        StringBuilder sb = new StringBuilder(50);
        for (int i = 0; i < 50; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
