package br.com.lc.nfse.web;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.dps.DpsBuilder;
import br.com.lc.nfse.core.dps.DpsPackager;
import br.com.lc.nfse.core.dps.DpsSigner;
import br.com.lc.nfse.core.dps.DpsValidator;
import br.com.lc.nfse.core.dps.RequisicaoDpsDto;
import br.com.lc.nfse.core.dps.ResultadoValidacao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Preview da emissão: recebe os dados do serviço, monta a DPS, valida contra o XSD,
 * assina (se houver certificado) e empacota. Não transmite (sem A1 no protótipo).
 */
@RestController
public class DpsPreviewController {

    private final DpsBuilder builder;
    private final DpsValidator validator;
    private final DpsSigner signer;
    private final DpsPackager packager;
    private final CertificadoProvider certificadoProvider;

    public DpsPreviewController(DpsBuilder builder, DpsValidator validator, DpsSigner signer,
                                DpsPackager packager, CertificadoProvider certificadoProvider) {
        this.builder = builder;
        this.validator = validator;
        this.signer = signer;
        this.packager = packager;
        this.certificadoProvider = certificadoProvider;
    }

    @PostMapping("/dps/preview")
    public ResponseEntity<RespostaDpsPreview> preview(@RequestBody RequisicaoDpsDto dto) {
        String xml = builder.construir(dto);

        ResultadoValidacao validacao = validator.validar(xml);
        if (!validacao.valido()) {
            return ResponseEntity.unprocessableEntity().body(new RespostaDpsPreview(
                    false, false, xml, null, "DPS inválida: " + validacao.mensagem()));
        }

        Optional<CertificadoLoader.Certificado> certificado = certificadoProvider.obter();
        if (certificado.isEmpty()) {
            return ResponseEntity.ok(new RespostaDpsPreview(
                    true, false, xml, null,
                    "DPS válida (não assinada: configure nfse.certificado.caminho para assinar)"));
        }

        String assinado = signer.assinar(xml, certificado.get());
        String pacote = packager.empacotar(assinado);
        return ResponseEntity.ok(new RespostaDpsPreview(
                true, true, assinado, pacote,
                "DPS válida e assinada — pronta para transmitir ao ADN quando houver A1"));
    }
}
