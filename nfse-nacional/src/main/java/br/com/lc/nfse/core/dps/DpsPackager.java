package br.com.lc.nfse.core.dps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Empacota/desempacota a DPS no formato que o ADN espera: gzip + base64.
 * Unidade pura — sem dependência de Spring.
 */
public class DpsPackager {

    /** XML assinado → gzip → base64 (string pronta para o corpo da requisição ao ADN). */
    public String empacotar(String xml) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
                gz.write(xml.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao empacotar DPS (gzip+base64)", e);
        }
    }

    /** base64 → gunzip → XML (usado nos testes de round-trip e para inspeção). */
    public String desempacotar(String base64Gzip) {
        try {
            byte[] gz = Base64.getDecoder().decode(base64Gzip);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz))) {
                return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao desempacotar DPS", e);
        }
    }
}
