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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Locale;

/** Gera o DANFSe v2.0 (réplica do layout oficial) em PDF — ambiente sandbox, sem valor fiscal. */
public class DanfsePdf {

    private static final Font F_TITULO = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font F_SUB = new Font(Font.HELVETICA, 7);
    private static final Font F_AVISO = new Font(Font.HELVETICA, 8, Font.BOLD, Color.RED);
    private static final Font F_SECAO = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final Font F_LABEL = new Font(Font.HELVETICA, 5, Font.NORMAL, new Color(110, 110, 110));
    private static final Font F_VAL = new Font(Font.HELVETICA, 7);
    private static final Color CINZA = new Color(224, 224, 224);
    private static final Color BORDA = new Color(150, 150, 150);

    public Path gerar(EmpresaInfo prest, String tomadorNome, String tomadorDoc, String codTributacao,
                      String descricao, String valor, String numeroNfse, String chaveAcesso,
                      String competencia, String dataHoraEmissao, String serieDps, String numeroDps,
                      String situacao, Path arquivo) throws Exception {
        Document doc = new Document(PageSize.A4, 22, 22, 22, 22);
        try (FileOutputStream fos = new FileOutputStream(arquivo.toFile())) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            // Cabeçalho
            PdfPTable head = new PdfPTable(new float[]{3, 2});
            head.setWidthPercentage(100);
            PdfPCell hl = semBorda();
            hl.addElement(new Paragraph("DANFSe v2.0", F_TITULO));
            hl.addElement(new Paragraph("Documento Auxiliar da NFS-e", F_SUB));
            hl.addElement(new Paragraph("AMBIENTE SANDBOX — SEM VALOR FISCAL", F_AVISO));
            head.addCell(hl);
            PdfPCell hr = semBorda();
            hr.addElement(par("Município: " + dash(prest.cidade())));
            hr.addElement(par("Ambiente Gerador: 2"));
            hr.addElement(par("Tipo de Ambiente: 2 (Homologação)"));
            head.addCell(hr);
            doc.add(head);
            doc.add(new Paragraph(" ", F_LABEL));

            // Chave de acesso
            PdfPTable chaveT = tabela(1);
            chaveT.addCell(campo("CHAVE DE ACESSO DA NFS-e", chaveAcesso));
            doc.add(chaveT);

            // Identificação NFS-e / DPS + QR
            Image qr = qrCode("https://www.nfse.gov.br/consulta/" + dash(chaveAcesso));
            PdfPTable idT = tabela(4);
            idT.addCell(campo("NÚMERO DA NFS-e", numeroNfse));
            idT.addCell(campo("COMPETÊNCIA", competencia));
            idT.addCell(campo("DATA/HORA EMISSÃO NFS-e", dataHoraEmissao));
            PdfPCell qrCell = new PdfPCell(qr, false);
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

            // Valores
            String vFmt = reais(valor);
            PdfPTable va = tabela(4);
            va.addCell(secao("VALORES DA NFS-e", 4));
            va.addCell(campo("VALOR TOTAL DA NFS-e", vFmt));
            va.addCell(campo("VALOR DA OPERAÇÃO / SERVIÇO", vFmt));
            va.addCell(campo("Desconto Incondicionado", "-"));
            va.addCell(campo("Desconto Condicionado", "-"));
            va.addCell(campo("Total das Retenções (ISSQN/Fed.)", "R$ 0,00"));
            va.addCell(campo("VALOR LÍQUIDO DA NFS-e", vFmt));
            va.addCell(campo("Total do IBS/CBS", "R$ 0,00"));
            va.addCell(campo("VALOR LÍQUIDO + IBS/CBS", vFmt));
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

    private PdfPTable tabela(int cols) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.getDefaultCell().setBorderColor(BORDA);
        return t;
    }

    private PdfPCell campo(String label, String valor) {
        return campoSpan(label, valor, 1);
    }

    private PdfPCell campoSpan(String label, String valor, int span) {
        PdfPCell c = new PdfPCell();
        c.setColspan(span);
        c.setBorderColor(BORDA);
        c.setPadding(2);
        c.setPaddingBottom(3);
        if (label != null && !label.isEmpty()) {
            Paragraph pl = new Paragraph(label, F_LABEL);
            pl.setLeading(6);
            c.addElement(pl);
        }
        Paragraph pv = new Paragraph(dash(valor), F_VAL);
        pv.setLeading(9);
        c.addElement(pv);
        return c;
    }

    private PdfPCell secao(String titulo, int span) {
        PdfPCell c = new PdfPCell(new Phrase(titulo, F_SECAO));
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
        return new Paragraph(s, F_SUB);
    }

    private Image qrCode(String conteudo) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 130, 130);
        BufferedImage bi = MatrixToImageWriter.toBufferedImage(matrix);
        Image img = Image.getInstance(bi, Color.WHITE);
        img.scaleAbsolute(62, 62);
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
