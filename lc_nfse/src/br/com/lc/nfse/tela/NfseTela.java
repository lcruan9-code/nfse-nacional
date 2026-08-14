package br.com.lc.nfse.tela;

import newpackage.CampoTexto;
import newpackage.CampoValorNumerico;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Tela de teste (padrão LC): lê a empresa, emite via nossa API sandbox e imprime o DANFSE. */
public class NfseTela extends JFrame {

    private final Config config;
    private final EmpresaInfo empresa;
    private final NfseApiClient api;

    private final CampoTexto descricao = new CampoTexto();
    private final CampoTexto codTrib = new CampoTexto();
    private final CampoValorNumerico valor = new CampoValorNumerico();
    private final JComboBox<Integer> tribIssqn = new JComboBox<>(new Integer[]{1, 2, 3, 4});
    private final JComboBox<Integer> tipoRet = new JComboBox<>(new Integer[]{1, 2, 3});
    private final JComboBox<String> simular = new JComboBox<>(new String[]{"AUTORIZADA", "REJEITADA"});
    private final CampoTexto tomadorNome = new CampoTexto();
    private final CampoTexto tomadorDoc = new CampoTexto();
    private final CampoValorNumerico aliqCbs = new CampoValorNumerico();
    private final CampoValorNumerico aliqIbs = new CampoValorNumerico();

    private final JButton btnTestar = new JButton("Testar conexão");
    private final JButton btnEmitir = new JButton("Emitir NFS-e");
    private final JButton btnImprimir = new JButton("Imprimir PDF");
    private final JLabel status = new JLabel(" Pronto.");
    private final JTextArea resultado = new JTextArea(4, 40);

    private String ultNumero;
    private String ultChave;
    private String ultAliqCbs;
    private String ultValorCbs;
    private String ultAliqIbs;
    private String ultValorIbs;

    public NfseTela(Config config, EmpresaInfo empresa) {
        super("LC Sistemas — Emissão de NFS-e (TESTE / SANDBOX)");
        this.config = config;
        this.empresa = empresa;
        this.api = new NfseApiClient(config);
        montar();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(740, 640);
        setLocationRelativeTo(null);
    }

    private void montar() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titulo = new JLabel("Emissão de NFS-e — " + empresa.fantasia());
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(titulo);
        main.add(Box.createVerticalStrut(8));

        main.add(painelPrestador());
        main.add(Box.createVerticalStrut(8));
        main.add(painelServico());
        main.add(Box.createVerticalStrut(8));
        main.add(painelTomador());
        main.add(Box.createVerticalStrut(8));
        main.add(painelReforma());
        main.add(Box.createVerticalStrut(8));

        resultado.setEditable(false);
        resultado.setBorder(new TitledBorder("Resultado"));
        resultado.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(resultado);
        main.add(Box.createVerticalStrut(6));

        codTrib.setText("010101");
        valor.setText(0.0);
        aliqCbs.setText(0.90);
        aliqIbs.setText(0.10);
        btnImprimir.setEnabled(false);
        btnTestar.addActionListener(e -> testar());
        btnEmitir.addActionListener(e -> emitir());
        btnImprimir.addActionListener(e -> imprimir());

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botoes.add(btnTestar);
        botoes.add(btnEmitir);
        botoes.add(btnImprimir);
        botoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(botoes);

        getContentPane().add(main, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);
    }

    private JPanel painelPrestador() {
        JPanel p = grid("Prestador (empresa " + config.empresaId + " do LC ERP)");
        p.add(new JLabel("Razão social:"));
        p.add(ro(empresa.razaoSocial()));
        p.add(new JLabel("CNPJ:"));
        p.add(ro(empresa.cnpjFormatado()));
        p.add(new JLabel("Inscrição municipal:"));
        p.add(ro(empresa.inscricaoMunicipal()));
        p.add(new JLabel("Município:"));
        p.add(ro(empresa.cidade() + " (IBGE " + empresa.ibge() + ")"));
        p.add(new JLabel("Regime:"));
        p.add(ro(empresa.regime() + " (CRT " + empresa.crt() + ")"));
        return p;
    }

    private JPanel painelServico() {
        JPanel p = grid("Serviço");
        descricao.setNumeroDeCaracteres(200);
        codTrib.setNumeroDeCaracteres(6);
        p.add(new JLabel("Descrição:"));
        p.add(descricao);
        p.add(new JLabel("Cód. tributação nacional:"));
        p.add(codTrib);
        p.add(new JLabel("Município prestação (IBGE):"));
        p.add(ro(empresa.ibge()));
        p.add(new JLabel("Valor do serviço (R$):"));
        p.add(valor);
        p.add(new JLabel("Tributação ISSQN:"));
        p.add(tribIssqn);
        p.add(new JLabel("Tipo de retenção:"));
        p.add(tipoRet);
        p.add(new JLabel("Simular (sandbox):"));
        p.add(simular);
        return p;
    }

    private JPanel painelTomador() {
        JPanel p = grid("Tomador (opcional — só no PDF)");
        p.add(new JLabel("Nome:"));
        p.add(tomadorNome);
        p.add(new JLabel("CNPJ/CPF:"));
        p.add(tomadorDoc);
        return p;
    }

    private JPanel painelReforma() {
        JPanel p = grid("Reforma Tributária — IBS/CBS (homologação)");
        p.add(new JLabel("Alíquota CBS (%):"));
        p.add(aliqCbs);
        p.add(new JLabel("Alíquota IBS (%):"));
        p.add(aliqIbs);
        return p;
    }

    private JPanel grid(String titulo) {
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 4));
        p.setBorder(new TitledBorder(titulo));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JComponent ro(String txt) {
        JTextField f = new JTextField(txt == null ? "" : txt);
        f.setEditable(false);
        f.setBackground(new Color(240, 240, 240));
        return f;
    }

    private void testar() {
        status.setText(" Testando conexão com " + config.apiBaseUrl + " ...");
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() {
                return api.testarConexao();
            }

            protected void done() {
                try {
                    status.setText(get()
                            ? " API no ar ✓ (" + config.apiBaseUrl + ")"
                            : " API NÃO respondeu — suba o Spring Boot (nfse-nacional) e o Postgres.");
                } catch (Exception ex) {
                    status.setText(" Erro: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void emitir() {
        final String desc = descricao.getText().trim();
        if (desc.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe a descrição do serviço.");
            return;
        }
        final String valorStr = String.format(Locale.US, "%.2f", valor.getValor());
        final String ct = codTrib.getText().trim();
        final int ti = (Integer) tribIssqn.getSelectedItem();
        final int tr = (Integer) tipoRet.getSelectedItem();
        final String sim = (String) simular.getSelectedItem();
        final String aCbs = String.format(Locale.US, "%.2f", aliqCbs.getValor());
        final String aIbs = String.format(Locale.US, "%.2f", aliqIbs.getValor());
        btnEmitir.setEnabled(false);
        status.setText(" Emitindo...");
        new SwingWorker<NfseApiClient.ConsultaResult, Void>() {
            protected NfseApiClient.ConsultaResult doInBackground() throws Exception {
                NfseApiClient.EmitirResult r = api.emitir(desc, ct, empresa.ibge(), valorStr, ti, tr, sim, aCbs, aIbs);
                return api.consultar(r.id());
            }

            protected void done() {
                btnEmitir.setEnabled(true);
                try {
                    NfseApiClient.ConsultaResult c = get();
                    if ("AUTORIZADA".equals(c.status())) {
                        ultNumero = c.numeroNfse();
                        ultChave = c.chaveAcesso();
                        ultAliqCbs = c.aliquotaCbs();
                        ultValorCbs = c.valorCbs();
                        ultAliqIbs = c.aliquotaIbs();
                        ultValorIbs = c.valorIbs();
                        resultado.setText("Status: AUTORIZADA\nNúmero: " + c.numeroNfse()
                                + "\nChave de acesso: " + c.chaveAcesso()
                                + "\nCBS " + c.aliquotaCbs() + "% = R$ " + c.valorCbs()
                                + "   |   IBS " + c.aliquotaIbs() + "% = R$ " + c.valorIbs());
                        btnImprimir.setEnabled(true);
                        status.setText(" NFS-e autorizada (sandbox). Pode imprimir o PDF.");
                    } else {
                        resultado.setText("Status: " + c.status() + "\nMotivo: " + c.motivo());
                        btnImprimir.setEnabled(false);
                        status.setText(" NFS-e " + c.status() + ".");
                    }
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    resultado.setText("Falha: " + msg);
                    status.setText(" Erro na emissão.");
                }
            }
        }.execute();
    }

    private void imprimir() {
        try {
            java.time.LocalDateTime agora = java.time.LocalDateTime.now();
            String comp = agora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String dh = agora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String regime = empresa.regime() == null ? "" : empresa.regime().toUpperCase();
            String situacao = regime.contains("MEI") || regime.contains("MICROEMPREEND") ? "NFS-e MEI"
                    : regime.contains("SIMPLES") ? "NFS-e Simples Nacional" : "NFS-e";
            String valorStr = String.format(Locale.US, "%.2f", valor.getValor());
            Path pdf = Paths.get(System.getProperty("java.io.tmpdir"),
                    "nfse-sandbox-" + (ultNumero == null ? "0" : ultNumero) + ".pdf");
            new DanfsePdf().gerar(empresa, tomadorNome.getText(), tomadorDoc.getText(), codTrib.getText().trim(),
                    descricao.getText(), valorStr, ultNumero, ultChave, comp, dh, "1", ultNumero, situacao,
                    ultAliqCbs, ultValorCbs, ultAliqIbs, ultValorIbs, pdf);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdf.toFile());
            }
            status.setText(" PDF gerado: " + pdf);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao gerar PDF: " + ex.getMessage());
        }
    }
}
