package br.com.lc.nfse.tela;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Lê lc_nfse.properties (MySQL do LC + credenciais fixas da nossa API sandbox). */
public class Config {

    public final String mysqlHost, mysqlPort, mysqlDb, mysqlUser, mysqlPass, empresaId;
    public final String apiBaseUrl, apiKey, apiEmpresaId;

    public Config(Properties p) {
        this.mysqlHost = p.getProperty("mysql.host", "127.0.0.1");
        this.mysqlPort = p.getProperty("mysql.port", "3306");
        this.mysqlDb = p.getProperty("mysql.db", "lc_relatorio");
        this.mysqlUser = p.getProperty("mysql.user", "root");
        this.mysqlPass = p.getProperty("mysql.pass", "");
        this.empresaId = p.getProperty("empresa.id", "1");
        this.apiBaseUrl = p.getProperty("api.baseUrl", "http://localhost:8080");
        this.apiKey = p.getProperty("api.key", "");
        this.apiEmpresaId = p.getProperty("api.empresaId", "");
    }

    public String mysqlUrl() {
        return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDb
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    /** Carrega do diretório de trabalho, ou ao lado do jar. */
    public static Config carregar() throws IOException {
        Path[] candidatos = { Paths.get("lc_nfse.properties"), Paths.get(System.getProperty("user.dir"), "lc_nfse.properties") };
        Properties p = new Properties();
        for (Path c : candidatos) {
            if (Files.exists(c)) {
                try (InputStream is = Files.newInputStream(c)) { p.load(is); }
                return new Config(p);
            }
        }
        throw new IOException("lc_nfse.properties não encontrado no diretório de execução");
    }
}
