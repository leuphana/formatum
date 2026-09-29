package de.leuphana.escience.formatum.testsupport;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.leuphana.escience.formatum.config.MetadataConfiguration;
import de.leuphana.escience.formatum.config.MetadataField;

/**
 * Builds {@link MetadataField} fixtures. The class has neither setters nor a public
 * constructor, so Jackson is the only way to create instances - which has the welcome
 * side effect of exercising the YAML mapping the production code relies on.
 */
public final class MetadataFields {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private MetadataFields() {
        // utility class
    }

    public static MetadataConfiguration configurationFromYaml(String yaml) {
        try {
            return YAML_MAPPER.readValue(yaml, MetadataConfiguration.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid YAML fixture", e);
        }
    }

    public static List<MetadataField> fromYaml(String yaml) {
        return configurationFromYaml(yaml).getMetadataFields();
    }

    /**
     * Shorthand for a single field, e.g. {@code field("dc.title", true, "Titel/Title")}.
     */
    public static MetadataField field(String name, boolean mandatory, String displayName) {
        return fromYaml("""
                metadata:
                  - field: "%s"
                    mandatory: %s
                    displayName: "%s"
                """.formatted(name, mandatory, displayName)).get(0);
    }
}
