package de.leuphana.escience.formatum.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "formatum")
public interface FormatumConfig {
    @WithName("metadata.config-path")
    String metadataConfigPath();

    @WithName("templates.path")
    String templatesPath();

    @WithName("templates.default-name")
    Optional<String> defaultTemplateName();
}
