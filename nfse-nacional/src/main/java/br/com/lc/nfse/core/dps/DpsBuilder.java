package br.com.lc.nfse.core.dps;

/**
 * Monta o XML da DPS (sem assinatura) a partir do {@link RequisicaoDpsDto}, na ordem
 * exigida pelo XSD. A corretude é garantida pelo {@link DpsValidator} (XSD como fonte
 * da verdade). Builder por template — controle exato de namespace/ordem, importante para
 * a canonicalização da assinatura. Unidade pura — sem Spring.
 */
public class DpsBuilder {

    private static final String NS = "http://www.sped.fazenda.gov.br/nfse";
    private static final String VERSAO = "1.00";
    private static final String VER_APLIC = "LC-NFSe-Proto-1.0";
    private static final String TIPO_INSC_CNPJ = "2";

    public String construir(RequisicaoDpsDto dto) {
        return construir(dto, null);
    }

    public String construir(RequisicaoDpsDto dto, IbsCbs ibsCbs) {
        String id = montarId(dto);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<DPS xmlns=\"").append(NS).append("\" versao=\"").append(VERSAO).append("\">");
        sb.append("<infDPS Id=\"").append(id).append("\">");
        sb.append("<tpAmb>2</tpAmb>");
        sb.append("<dhEmi>").append(dto.dhEmiUtc()).append("</dhEmi>");
        sb.append("<verAplic>").append(VER_APLIC).append("</verAplic>");
        sb.append("<serie>").append(dto.serie()).append("</serie>");
        sb.append("<nDPS>").append(dto.numero()).append("</nDPS>");
        sb.append("<dCompet>").append(dto.dataCompetencia()).append("</dCompet>");
        sb.append("<tpEmit>1</tpEmit>");
        sb.append("<cLocEmi>").append(dto.codMunEmissor()).append("</cLocEmi>");

        // Prestador
        sb.append("<prest>");
        sb.append("<CNPJ>").append(dto.cnpjPrestador()).append("</CNPJ>");
        sb.append("<regTrib>");
        sb.append("<opSimpNac>").append(dto.opSimplesNacional()).append("</opSimpNac>");
        sb.append("<regEspTrib>").append(dto.regimeEspecial()).append("</regEspTrib>");
        sb.append("</regTrib>");
        sb.append("</prest>");

        // Serviço
        sb.append("<serv>");
        sb.append("<locPrest><cLocPrestacao>").append(dto.codMunPrestacao()).append("</cLocPrestacao></locPrest>");
        sb.append("<cServ>");
        sb.append("<cTribNac>").append(dto.codTribNacional()).append("</cTribNac>");
        sb.append("<xDescServ>").append(escapar(dto.descricaoServico())).append("</xDescServ>");
        sb.append("</cServ>");
        sb.append("</serv>");

        // Valores
        sb.append("<valores>");
        sb.append("<vServPrest><vServ>").append(dto.valorServico()).append("</vServ></vServPrest>");
        sb.append("<trib>");
        sb.append("<tribMun>");
        sb.append("<tribISSQN>").append(dto.tributacaoIssqn()).append("</tribISSQN>");
        sb.append("<tpRetISSQN>").append(dto.tipoRetencaoIssqn()).append("</tpRetISSQN>");
        sb.append("</tribMun>");
        sb.append("<totTrib><indTotTrib>0</indTotTrib></totTrib>");
        sb.append("</trib>");
        sb.append("</valores>");

        if (ibsCbs != null) {
            sb.append("<IBSCBS>");
            sb.append("<finNFSe>").append(ibsCbs.finNFSe()).append("</finNFSe>");
            sb.append("<cIndOp>").append(ibsCbs.cIndOp()).append("</cIndOp>");
            sb.append("<indDest>").append(ibsCbs.indDest()).append("</indDest>");
            sb.append("<valores><trib><gIBSCBS>");
            sb.append("<CST>").append(ibsCbs.cst()).append("</CST>");
            sb.append("<cClassTrib>").append(ibsCbs.cClassTrib()).append("</cClassTrib>");
            sb.append("</gIBSCBS></trib></valores>");
            sb.append("</IBSCBS>");
        }

        sb.append("</infDPS>");
        sb.append("</DPS>");
        return sb.toString();
    }

    /** Id de 45 posições: "DPS" + IBGE(7) + tipoInsc(1) + CNPJ(14) + série(5) + nº(15). */
    private String montarId(RequisicaoDpsDto dto) {
        return "DPS"
                + dto.codMunEmissor()
                + TIPO_INSC_CNPJ
                + leftPadZeros(dto.cnpjPrestador(), 14)
                + leftPadZeros(dto.serie(), 5)
                + leftPadZeros(dto.numero(), 15);
    }

    private String leftPadZeros(String valor, int tamanho) {
        String s = valor == null ? "" : valor.trim();
        if (s.length() >= tamanho) {
            return s.substring(s.length() - tamanho);
        }
        return "0".repeat(tamanho - s.length()) + s;
    }

    private String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
