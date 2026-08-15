package br.com.lc.nfse.core.municipal.abrasf;

import br.com.lc.nfse.core.xsd.ResultadoValidacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfValidatorTest {

    private static final String NS = "http://www.abrasf.org.br/nfse.xsd";

    private String gerarNfseEnvioMinimo() {
        return """
            <GerarNfseEnvio xmlns="%s">
              <Rps>
                <InfDeclaracaoPrestacaoServico Id="rps1">
                  <Rps>
                    <IdentificacaoRps><Numero>1</Numero><Serie>1</Serie><Tipo>1</Tipo></IdentificacaoRps>
                    <DataEmissao>2026-08-14</DataEmissao>
                    <Status>1</Status>
                  </Rps>
                  <Competencia>2026-08-14</Competencia>
                  <Servico>
                    <Valores><ValorServicos>1500.00</ValorServicos></Valores>
                    <IssRetido>2</IssRetido>
                    <ItemListaServico>01.01</ItemListaServico>
                    <Discriminacao>Consultoria em TI</Discriminacao>
                    <CodigoMunicipio>4204608</CodigoMunicipio>
                    <ExigibilidadeISS>1</ExigibilidadeISS>
                  </Servico>
                  <Prestador>
                    <CpfCnpj><Cnpj>11222333000181</Cnpj></CpfCnpj>
                    <InscricaoMunicipal>123</InscricaoMunicipal>
                  </Prestador>
                  <OptanteSimplesNacional>2</OptanteSimplesNacional>
                  <IncentivoFiscal>2</IncentivoFiscal>
                </InfDeclaracaoPrestacaoServico>
              </Rps>
            </GerarNfseEnvio>
            """.formatted(NS);
    }

    @Test
    void xmlMinimoValido_passa() {
        ResultadoValidacao r = new AbrasfValidator().validar(gerarNfseEnvioMinimo());
        assertThat(r.valido()).as(r.mensagem()).isTrue();
    }

    @Test
    void xmlSemValorServicos_falha() {
        String xml = gerarNfseEnvioMinimo().replace(
            "<Valores><ValorServicos>1500.00</ValorServicos></Valores>", "<Valores></Valores>");
        assertThat(new AbrasfValidator().validar(xml).valido()).isFalse();
    }
}
