package br.com.lc.nfse.tela;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cliente da nossa API sandbox de NFS-e. Monta o JSON à mão e faz parse simples. */
public class NfseApiClient {

    private final Config config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public NfseApiClient(Config config) {
        this.config = config;
    }

    public record EmitirResult(String id, String status) {}

    public record ConsultaResult(String status, String chaveAcesso, String numeroNfse, String motivo) {}

    public boolean testarConexao() {
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(config.apiBaseUrl + "/v1/whoami")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 401 || r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public EmitirResult emitir(String descricao, String codTribNac, String codMunPrest,
                               String valor, int tribIssqn, int tipoRet, String simular) throws Exception {
        String body = "{"
                + "\"empresaId\":\"" + config.apiEmpresaId + "\","
                + "\"servico\":{\"codTribNacional\":\"" + esc(codTribNac) + "\",\"descricao\":\"" + esc(descricao)
                + "\",\"codMunPrestacao\":\"" + esc(codMunPrest) + "\"},"
                + "\"valores\":{\"valorServico\":\"" + esc(valor) + "\",\"tributacaoIssqn\":" + tribIssqn
                + ",\"tipoRetencaoIssqn\":" + tipoRet + "},"
                + "\"simular\":\"" + esc(simular) + "\"}";
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(config.apiBaseUrl + "/v1/nfse"))
                        .header("X-Api-Key", config.apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 202) {
            throw new RuntimeException("Emissão recusada (HTTP " + r.statusCode() + "): " + r.body());
        }
        return new EmitirResult(campo(r.body(), "id"), campo(r.body(), "status"));
    }

    public ConsultaResult consultar(String id) throws Exception {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(config.apiBaseUrl + "/v1/nfse/" + id))
                        .header("X-Api-Key", config.apiKey).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new RuntimeException("Consulta falhou (HTTP " + r.statusCode() + "): " + r.body());
        }
        String b = r.body();
        return new ConsultaResult(campo(b, "status"), campo(b, "chaveAcesso"),
                campo(b, "numeroNfse"), campo(b, "motivo"));
    }

    /** Extrai "nome":"valor" (ou null) de um JSON plano. */
    private static String campo(String json, String nome) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(nome)
                + "\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|null)").matcher(json);
        if (m.find()) {
            return m.group(1) == null ? null : m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return null;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
