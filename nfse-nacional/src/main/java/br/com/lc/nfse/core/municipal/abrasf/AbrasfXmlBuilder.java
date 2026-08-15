package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.municipal.RpsRequest;

import java.time.format.DateTimeFormatter;

/** Monta o GerarNfseEnvio (ABRASF). Ramifica por versão; nesta fatia só a 2.04. */
public class AbrasfXmlBuilder {

    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String montar(RpsRequest r, String versaoAbrasf, String idInfDeclaracao) {
        if (!"2.04".equals(versaoAbrasf)) {
            throw new IllegalArgumentException("Versão ABRASF não suportada nesta fatia: " + versaoAbrasf);
        }
        String discriminacao = esc(r.discriminacao());
        return """
            <GerarNfseEnvio xmlns="%s"><Rps><InfDeclaracaoPrestacaoServico Id="%s">\
            <Rps><IdentificacaoRps><Numero>%s</Numero><Serie>%s</Serie><Tipo>%s</Tipo></IdentificacaoRps>\
            <DataEmissao>%s</DataEmissao><Status>1</Status></Rps>\
            <Competencia>%s</Competencia>\
            <Servico><Valores><ValorServicos>%s</ValorServicos></Valores>\
            <IssRetido>%d</IssRetido><ItemListaServico>%s</ItemListaServico>\
            <Discriminacao>%s</Discriminacao><CodigoMunicipio>%s</CodigoMunicipio>\
            <ExigibilidadeISS>%d</ExigibilidadeISS></Servico>\
            <Prestador><CpfCnpj><Cnpj>%s</Cnpj></CpfCnpj><InscricaoMunicipal>%s</InscricaoMunicipal></Prestador>\
            <OptanteSimplesNacional>%d</OptanteSimplesNacional><IncentivoFiscal>%d</IncentivoFiscal>\
            </InfDeclaracaoPrestacaoServico></Rps></GerarNfseEnvio>"""
            .formatted(NS, idInfDeclaracao,
                r.numero(), r.serie(), r.tipo(),
                r.dataEmissao().format(DATA), r.competencia().format(DATA),
                r.valorServicos(), r.issRetido(), r.itemListaServico(),
                discriminacao, r.codigoMunicipioIbge(), r.exigibilidadeIss(),
                r.prestadorCnpj(), r.prestadorInscricaoMunicipal(),
                r.optanteSimplesNacional(), r.incentivoFiscal());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
