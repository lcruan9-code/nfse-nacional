package br.com.lc.nfse.tela;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Gera o DANFSe v2.0 conforme a NT 008/2026: logo oficial, fontes Arial (títulos/rótulos) +
 * Microsoft Sans Serif (conteúdo), frase "NFS-e SEM VALIDADE JURÍDICA" (homologação), QR code
 * (>= 1,52 cm) e sombreamento no valor líquido. Ambiente sandbox — sem valor jurídico.
 */
public class DanfsePdf {

    private static final Color CINZA = new Color(224, 224, 224);
    private static final Color BORDA = new Color(150, 150, 150);
    private static final Color DESTAQUE = new Color(232, 232, 232);
    private static final String FONTES = "C:/Windows/Fonts/";

    private final Font fTitulo;
    private final Font fSub;
    private final Font fSecao;
    private final Font fLabel;
    private final Font fVal;
    private final Font fAviso;

    public DanfsePdf() {
        Font titulo, sub, secao, label, val, aviso;
        try {
            BaseFont arial = BaseFont.createFont(FONTES + "arial.ttf", BaseFont.WINANSI, BaseFont.EMBEDDED);
            BaseFont arialBd = BaseFont.createFont(FONTES + "arialbd.ttf", BaseFont.WINANSI, BaseFont.EMBEDDED);
            BaseFont sans = BaseFont.createFont(FONTES + "micross.ttf", BaseFont.WINANSI, BaseFont.EMBEDDED);
            titulo = new Font(arialBd, 13);
            sub = new Font(arial, 7);
            secao = new Font(arialBd, 7);
            label = new Font(arial, 5, Font.NORMAL, new Color(110, 110, 110));
            val = new Font(sans, 7);
            aviso = new Font(arialBd, 10, Font.NORMAL, Color.RED);
        } catch (Exception e) {
            titulo = new Font(Font.HELVETICA, 13, Font.BOLD);
            sub = new Font(Font.HELVETICA, 7);
            secao = new Font(Font.HELVETICA, 7, Font.BOLD);
            label = new Font(Font.HELVETICA, 5, Font.NORMAL, new Color(110, 110, 110));
            val = new Font(Font.HELVETICA, 7);
            aviso = new Font(Font.HELVETICA, 10, Font.BOLD, Color.RED);
        }
        this.fTitulo = titulo;
        this.fSub = sub;
        this.fSecao = secao;
        this.fLabel = label;
        this.fVal = val;
        this.fAviso = aviso;
    }

    public Path gerar(EmpresaInfo prest, String tomadorNome, String tomadorDoc, String codTributacao,
                      String descricao, String valor, String numeroNfse, String chaveAcesso,
                      String competencia, String dataHoraEmissao, String serieDps, String numeroDps,
                      String situacao, Path arquivo) throws Exception {
        Document doc = new Document(PageSize.A4, 22, 22, 22, 22);
        try (FileOutputStream fos = new FileOutputStream(arquivo.toFile())) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            // Cabeçalho: logo | título centralizado | município
            PdfPTable head = new PdfPTable(new float[]{2.2f, 3f, 2.2f});
            head.setWidthPercentage(100);
            PdfPCell logoCell = semBorda();
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.addElement(logo());
            head.addCell(logoCell);
            PdfPCell titleCell = semBorda();
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.addElement(centralizado("DANFSe v2.0", fTitulo));
            titleCell.addElement(centralizado("Documento Auxiliar da NFS-e", fSub));
            head.addCell(titleCell);
            PdfPCell munCell = semBorda();
            munCell.addElement(par("Município: " + dash(prest.cidade())));
            munCell.addElement(par("Ambiente Gerador: 2"));
            munCell.addElement(par("Tipo de Ambiente: 2 (Homologação)"));
            head.addCell(munCell);
            doc.add(head);

            Paragraph avisoP = centralizado("NFS-e SEM VALIDADE JURÍDICA", fAviso);
            avisoP.setSpacingBefore(2);
            avisoP.setSpacingAfter(4);
            doc.add(avisoP);

            // Chave de acesso
            PdfPTable chaveT = tabela(1);
            chaveT.addCell(campo("CHAVE DE ACESSO DA NFS-e", chaveAcesso));
            doc.add(chaveT);

            // Identificação NFS-e / DPS + QR
            PdfPTable idT = tabela(4);
            idT.addCell(campo("NÚMERO DA NFS-e", numeroNfse));
            idT.addCell(campo("COMPETÊNCIA", competencia));
            idT.addCell(campo("DATA/HORA EMISSÃO NFS-e", dataHoraEmissao));
            PdfPCell qrCell = new PdfPCell(qrCode("https://www.nfse.gov.br/consulta/" + dash(chaveAcesso)), false);
            qrCell.setRowspan(2);
            qrCell.setBorderColor(BORDA);
            qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            qrCell.setPadding(4);
            idT.addCell(qrCell);
            idT.addCell(campo("NÚMERO DA DPS", numeroDps));
            idT.addCell(campo("SÉRIE DA DPS", serieDps));
            idT.addCell(campo("DATA/HORA EMISSÃO DPS", dataHoraEmissao));
            doc.add(idT);

            PdfPTable emT = tabela(3);
            emT.addCell(campo("EMITENTE DA NFS-e", "Prestador"));
            emT.addCell(campo("SITUAÇÃO DA NFS-e", situacao));
            emT.addCell(campo("FINALIDADE", "-"));
            doc.add(emT);

            // Prestador
            PdfPTable pr = tabela(4);
            pr.addCell(secao("PRESTADOR / FORNECEDOR", 4));
            pr.addCell(campo("CNPJ / CPF / NIF", prest.cnpjFormatado()));
            pr.addCell(campo("Inscrição Municipal", prest.inscricaoMunicipal()));
            pr.addCell(campoSpan("Telefone", "-", 2));
            pr.addCell(campoSpan("Nome / Nome Empresarial", prest.razaoSocial(), 2));
            pr.addCell(campo("Município / Sigla UF", dash(prest.cidade())));
            pr.addCell(campo("Código IBGE / CEP", dash(prest.ibge()) + " / -"));
            pr.addCell(campoSpan("Endereço", "-", 3));
            pr.addCell(campo("E-mail", "-"));
            pr.addCell(campoSpan("Simples Nacional na Data de Competência", dash(prest.regime()), 2));
            pr.addCell(campoSpan("Regime de Apuração pelo SN", "-", 2));
            doc.add(pr);

            // Tomador
            PdfPTable to = tabela(4);
            to.addCell(secao("TOMADOR / ADQUIRENTE", 4));
            to.addCell(campo("CNPJ / CPF / NIF", tomadorDoc));
            to.addCell(campo("Inscrição Municipal", "-"));
            to.addCell(campoSpan("Telefone", "-", 2));
            to.addCell(campoSpan("Nome / Nome Empresarial", tomadorNome, 2));
            to.addCell(campo("Município / Sigla UF", "-"));
            to.addCell(campo("Código IBGE / CEP", "-"));
            to.addCell(campoSpan("Endereço", "-", 3));
            to.addCell(campo("E-mail", "-"));
            doc.add(to);

            // Serviço
            PdfPTable se = tabela(4);
            se.addCell(secao("SERVIÇO PRESTADO", 4));
            se.addCell(campo("Cód. Tributação Nac./Mun.", dash(codTributacao) + " / -"));
            se.addCell(campo("Código da NBS", "-"));
            se.addCell(campoSpan("Local da Prestação / UF / País", dash(prest.cidade()) + " / - / -", 2));
            se.addCell(campoSpan("Descrição do Serviço", descricao, 4));
            doc.add(se);

            // ISSQN
            PdfPTable is = tabela(4);
            is.addCell(secao("TRIBUTAÇÃO MUNICIPAL (ISSQN)", 4));
            is.addCell(campoSpan("Tipo de Tributação do ISSQN", "Operação Tributável", 2));
            is.addCell(campoSpan("Município / UF / País de Incidência", dash(prest.cidade()) + " / - / -", 2));
            is.addCell(campo("BC ISSQN", "-"));
            is.addCell(campo("Alíquota Aplicada", "-"));
            is.addCell(campo("Retenção do ISSQN", "Não Retido"));
            is.addCell(campo("ISSQN Apurado", "-"));
            doc.add(is);

            // Federal
            PdfPTable fe = tabela(4);
            fe.addCell(secao("TRIBUTAÇÃO FEDERAL (EXCETO CBS)", 4));
            fe.addCell(campo("IRRF", "-"));
            fe.addCell(campo("Contrib. Previdenciária Retida", "-"));
            fe.addCell(campoSpan("Contribuições Sociais Retidas", "-", 2));
            fe.addCell(campo("PIS", "-"));
            fe.addCell(campo("COFINS", "-"));
            fe.addCell(campoSpan("Descrição Contrib. Sociais Retidas", "-", 2));
            doc.add(fe);

            // IBS/CBS
            PdfPTable ib = tabela(4);
            ib.addCell(secao("TRIBUTAÇÃO IBS / CBS", 4));
            ib.addCell(campo("CST / cClassTrib", "- / -"));
            ib.addCell(campoSpan("Indicador Operação / IBGE / Município / UF", "- / - / - / -", 3));
            ib.addCell(campo("Exclusões e Reduções da BC", "R$ 0,00"));
            ib.addCell(campo("BC Após Exclusões/Reduções", "-"));
            ib.addCell(campo("Red. Alíquota IBS / CBS", "- / -"));
            ib.addCell(campo("Alíquota IBS UF / Mun", "- / -"));
            ib.addCell(campo("Alíq. Efetiva Mun. - IBS", "-"));
            ib.addCell(campo("Valor Apurado Mun. - IBS", "-"));
            ib.addCell(campo("Alíq. Efetiva Est. - IBS", "-"));
            ib.addCell(campo("Valor Apurado Est. - IBS", "-"));
            ib.addCell(campo("Valor Total Apurado - IBS", "-"));
            ib.addCell(campo("Alíquota - CBS", "-"));
            ib.addCell(campo("Alíq. Efetiva - CBS", "-"));
            ib.addCell(campo("Valor Total Apurado - CBS", "-"));
            doc.add(ib);

            // Valores (com sombreamento nos destaques)
            String vFmt = reais(valor);
            PdfPTable va = tabela(4);
            va.addCell(secao("VALORES DA NFS-e", 4));
            va.addCell(campoDestaque("VALOR TOTAL DA NFS-e", vFmt));
            va.addCell(campo("VALOR DA OPERAÇÃO / SERVIÇO", vFmt));
            va.addCell(campo("Desconto Incondicionado", "-"));
            va.addCell(campo("Desconto Condicionado", "-"));
            va.addCell(campo("Total das Retenções (ISSQN/Fed.)", "R$ 0,00"));
            va.addCell(campo("VALOR LÍQUIDO DA NFS-e", vFmt));
            va.addCell(campo("Total do IBS/CBS", "R$ 0,00"));
            va.addCell(campoDestaque("VALOR LÍQUIDO DA NFS-e + IBS/CBS", vFmt));
            doc.add(va);

            // Complementares
            PdfPTable co = tabela(1);
            co.addCell(secao("INFORMAÇÕES COMPLEMENTARES", 1));
            co.addCell(campo("", "Totais aproximados dos Tributos cfe. Lei nº 12.741/2012: "
                    + "Federais: -; Estaduais: -; Municipais: -;"));
            doc.add(co);

            // Rodapé
            PdfPTable rp = tabela(3);
            rp.addCell(campo("DATA CIENTIFICAÇÃO", "-"));
            rp.addCell(campo("IDENTIFICAÇÃO E ASSINATURA", "-"));
            rp.addCell(campo("Nº NFS-e / CHAVE NFS-e", dash(numeroNfse) + " / " + dash(chaveAcesso)));
            doc.add(rp);

            doc.close();
        }
        return arquivo;
    }

    // ---- helpers ----

    private Element logo() throws Exception {
        Path logoPath = Paths.get("logo-nfse.png");
        if (Files.exists(logoPath)) {
            Image img = Image.getInstance(logoPath.toString());
            img.scaleToFit(140, 44);
            return img;
        }
        return new Paragraph("NFS-e", new Font(fTitulo.getBaseFont() != null ? fTitulo.getBaseFont() : null,
                18, Font.BOLD, new Color(0, 150, 70)));
    }

    private PdfPTable tabela(int cols) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.getDefaultCell().setBorderColor(BORDA);
        return t;
    }

    private PdfPCell campo(String label, String valor) {
        return campoSpan(label, valor, 1);
    }

    private PdfPCell campoDestaque(String label, String valor) {
        PdfPCell c = campoSpan(label, valor, 1);
        c.setBackgroundColor(DESTAQUE);
        return c;
    }

    private PdfPCell campoSpan(String label, String valor, int span) {
        PdfPCell c = new PdfPCell();
        c.setColspan(span);
        c.setBorderColor(BORDA);
        c.setPadding(2);
        c.setPaddingBottom(3);
        if (label != null && !label.isEmpty()) {
            Paragraph pl = new Paragraph(label, fLabel);
            pl.setLeading(6);
            c.addElement(pl);
        }
        Paragraph pv = new Paragraph(dash(valor), fVal);
        pv.setLeading(9);
        c.addElement(pv);
        return c;
    }

    private PdfPCell secao(String titulo, int span) {
        PdfPCell c = new PdfPCell(new Phrase(titulo, fSecao));
        c.setColspan(span);
        c.setBackgroundColor(CINZA);
        c.setBorderColor(BORDA);
        c.setPadding(3);
        return c;
    }

    private PdfPCell semBorda() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private Paragraph par(String s) {
        return new Paragraph(s, fSub);
    }

    private Paragraph centralizado(String s, Font f) {
        Paragraph p = new Paragraph(s, f);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private Image qrCode(String conteudo) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 130, 130);
        BufferedImage bi = MatrixToImageWriter.toBufferedImage(matrix);
        Image img = Image.getInstance(bi, Color.WHITE);
        img.scaleAbsolute(62, 62); // ~2,2 cm (mínimo NT: 1,52 cm)
        return img;
    }

    private static String dash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s;
    }

    private static String reais(String v) {
        try {
            return String.format(new Locale("pt", "BR"), "R$ %,.2f", Double.parseDouble(v));
        } catch (Exception e) {
            return "R$ " + v;
        }
    }
}
