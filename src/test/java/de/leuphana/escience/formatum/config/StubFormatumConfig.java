package de.leuphana.escience.formatum.config;

import java.util.Optional;

/**
 * Hand written stand-in for the {@link FormatumConfig} config mapping interface, which
 * cannot be instantiated outside of the SmallRye config system.
 */
public class StubFormatumConfig implements FormatumConfig {

    private String metadataConfigPath;
    private String templatesPath;
    private Optional<String> defaultTemplateName = Optional.empty();

    @Override
    public String metadataConfigPath() {
        return metadataConfigPath;
    }

    @Override
    public String templatesPath() {
        return templatesPath;
    }

    @Override
    public Optional<String> defaultTemplateName() {
        return defaultTemplateName;
    }

    public StubFormatumConfig withMetadataConfigPath(String path) {
        this.metadataConfigPath = path;
        return this;
    }

    public StubFormatumConfig withTemplatesPath(String path) {
        this.templatesPath = path;
        return this;
    }

    public StubFormatumConfig withDefaultTemplateName(String name) {
        this.defaultTemplateName = Optional.ofNullable(name);
        return this;
    }
}
