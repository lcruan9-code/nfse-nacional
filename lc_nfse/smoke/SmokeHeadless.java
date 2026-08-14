import br.com.lc.nfse.tela.Config;
import br.com.lc.nfse.tela.DanfsePdf;
import br.com.lc.nfse.tela.EmpresaDao;
import br.com.lc.nfse.tela.EmpresaInfo;
import br.com.lc.nfse.tela.NfseApiClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Smoke headless: Config -> DAO (MySQL real) -> API (se no ar) -> PDF (iText). */
public class SmokeHeadless {

    public static void main(String[] args) throws Exception {
        Config cfg = Config.carregar();
        System.out.println("[config] api=" + cfg.apiBaseUrl + " empresaId=" + cfg.apiEmpresaId);

        EmpresaInfo emp = new EmpresaDao(cfg).buscar(cfg.empresaId);
        System.out.println("[DAO] " + emp.razaoSocial() + " | CNPJ " + emp.cnpjFormatado()
                + " | IBGE " + emp.ibge() + " | " + emp.regime() + " (CRT " + emp.crt() + ")");

        NfseApiClient api = new NfseApiClient(cfg);
        boolean noAr = api.testarConexao();
        System.out.println("[API] conexao=" + noAr);

        String numero = "1";
        String chave = "00000000000000000000000000000000000000000000000000";
        String aCbs = "0.90", vCbs = "0.00", aIbs = "0.10", vIbs = "0.00";
        if (noAr) {
            NfseApiClient.EmitirResult r = api.emitir("Servico de teste headless", "010101", emp.ibge(),
                    "1234.56", 1, 1, "AUTORIZADA", "0.90", "0.10");
            System.out.println("[API] emitido id=" + r.id() + " status=" + r.status());
            NfseApiClient.ConsultaResult c = api.consultar(r.id());
            System.out.println("[API] consulta status=" + c.status() + " chave=" + c.chaveAcesso()
                    + " numero=" + c.numeroNfse() + " | CBS " + c.aliquotaCbs() + "%=R$" + c.valorCbs()
                    + " IBS " + c.aliquotaIbs() + "%=R$" + c.valorIbs());
            numero = c.numeroNfse();
            chave = c.chaveAcesso();
            aCbs = c.aliquotaCbs();
            vCbs = c.valorCbs();
            aIbs = c.aliquotaIbs();
            vIbs = c.valorIbs();
        } else {
            System.out.println("[API] fora do ar — PDF sera gerado com dados fixos");
        }

        Path pdf = Paths.get(System.getProperty("java.io.tmpdir"), "lc_nfse_smoke.pdf");
        new DanfsePdf().gerar(emp, "Cliente Teste LTDA", "12345678000199", "010101", "Servico de teste headless",
                "1234.56", numero, chave, "14/08/2026", "14/08/2026 10:00:00", "1", numero,
                "NFS-e Simples Nacional", aCbs, vCbs, aIbs, vIbs, pdf);
        System.out.println("[PDF] gerado=" + Files.exists(pdf) + " tamanho=" + Files.size(pdf) + " (" + pdf + ")");
        System.out.println("SMOKE OK");
    }
}
