package br.com.lc.nfse.tela;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.FileOutputStream;
import java.nio.file.Path;

/** Gera um DANFSE fictício (sandbox) em PDF com iText 2.1.7. */
public class DanfsePdf {

    public Path gerar(EmpresaInfo prest, String tomadorNome, String tomadorDoc, String descricao,
                      String valor, String numero, String chaveAcesso, Path arquivo) throws Exception {
        Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
        try (FileOutputStream fos = new FileOutputStream(arquivo.toFile())) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            Font tituloF = new Font(Font.HELVETICA, 15, Font.BOLD);
            Font avisoF = new Font(Font.HELVETICA, 11, Font.BOLD, Color.RED);
            Font secaoF = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font rotF = new Font(Font.HELVETICA, 9, Font.BOLD);
            Font valF = new Font(Font.HELVETICA, 9);

            Paragraph titulo = new Paragraph("NFS-e — Nota Fiscal de Serviços Eletrônica", tituloF);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph aviso = new Paragraph("AMBIENTE SANDBOX — SEM VALOR FISCAL", avisoF);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(14);
            doc.add(aviso);

            doc.add(linha("Número da NFS-e:", numero, rotF, valF));
            doc.add(linha("Chave de acesso:", chaveAcesso, rotF, valF));

            doc.add(secao("PRESTADOR", secaoF));
            doc.add(linha("Razão social:", prest.razaoSocial(), rotF, valF));
            doc.add(linha("CNPJ:", prest.cnpjFormatado(), rotF, valF));
            doc.add(linha("Município:", prest.cidade() + " (IBGE " + prest.ibge() + ")", rotF, valF));
            doc.add(linha("Regime:", prest.regime(), rotF, valF));

            doc.add(secao("TOMADOR", secaoF));
            doc.add(linha("Nome:", vazioOuTraco(tomadorNome), rotF, valF));
            doc.add(linha("Documento:", vazioOuTraco(tomadorDoc), rotF, valF));

            doc.add(secao("SERVIÇO", secaoF));
            doc.add(linha("Descrição:", descricao, rotF, valF));
            doc.add(linha("Valor total:", "R$ " + valor, rotF, valF));

            doc.close();
        }
        return arquivo;
    }

    private Paragraph linha(String rotulo, String valor, Font rotF, Font valF) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(rotulo + " ", rotF));
        p.add(new Chunk(valor == null ? "-" : valor, valF));
        p.setSpacingAfter(3);
        return p;
    }

    private Paragraph secao(String titulo, Font f) {
        Paragraph p = new Paragraph(titulo, f);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private String vazioOuTraco(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
