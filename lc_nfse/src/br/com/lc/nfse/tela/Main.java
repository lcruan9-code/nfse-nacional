package br.com.lc.nfse.tela;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Launcher: carrega config, lê a empresa do MySQL e abre a tela. */
public class Main {

    public static void main(String[] args) {
        try {
            Config config = Config.carregar();
            EmpresaInfo empresa = new EmpresaDao(config).buscar(config.empresaId);
            SwingUtilities.invokeLater(() -> new NfseTela(config, empresa).setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro ao iniciar lc_nfse:\n" + e.getMessage(),
                    "lc_nfse", JOptionPane.ERROR_MESSAGE);
        }
    }
}
