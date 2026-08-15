package br.com.lc.nfse.core.xsd;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remove a âncora inicial {@code ^} e a final {@code $} de cada {@code <xs:pattern>}/{@code <xsd:pattern>}.
 * Os XSDs fiscais BR foram escritos com semântica de regex .NET (^...$); o Xerces trata ^ e $ como
 * literais e rejeita valores válidos. Saneamos antes de compilar o schema.
 */
public final class SanitizadorAncoras {

    private static final Pattern PATTERN_FACET =
            Pattern.compile("(<xs[d]?:pattern\\s+value=\")([^\"]*)(\")");

    private SanitizadorAncoras() {}

    public static String sanear(String xsd) {
        Matcher m = PATTERN_FACET.matcher(xsd);
        StringBuilder sb = new StringBuilder(xsd.length());
        while (m.find()) {
            String valor = m.group(2);
            if (valor.startsWith("^")) valor = valor.substring(1);
            if (valor.endsWith("$")) valor = valor.substring(0, valor.length() - 1);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + valor + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
