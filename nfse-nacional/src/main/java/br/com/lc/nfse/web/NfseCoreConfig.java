package br.com.lc.nfse.web;

import br.com.lc.nfse.core.cert.CertificadoLoader;
import br.com.lc.nfse.core.dps.DpsBuilder;
import br.com.lc.nfse.core.dps.DpsPackager;
import br.com.lc.nfse.core.dps.DpsSigner;
import br.com.lc.nfse.core.dps.DpsValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra as unidades do núcleo como beans, mantendo o pacote {@code core} sem Spring. */
@Configuration
public class NfseCoreConfig {

    @Bean
    DpsBuilder dpsBuilder() {
        return new DpsBuilder();
    }

    @Bean
    DpsValidator dpsValidator() {
        return new DpsValidator();
    }

    @Bean
    DpsSigner dpsSigner() {
        return new DpsSigner();
    }

    @Bean
    DpsPackager dpsPackager() {
        return new DpsPackager();
    }

    @Bean
    CertificadoLoader certificadoLoader() {
        return new CertificadoLoader();
    }
}
