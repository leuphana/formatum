package de.leuphana.escience.formatum.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.quarkus.runtime.configuration.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code onStart} is package private and its {@code StartupEvent} argument is unused,
 * so the startup behaviour can be driven directly, without booting Quarkus.
 */
class MetadataConfigurationServiceTest {

    private static final String YAML = """
            metadata:
              - field: "dc.title"
                mandatory: true
                displayName: "Titel/Title"
              - field: "dc.description.abstract"
                mandatory: false
                displayName: "Zusammenfassung/Abstract"
            """;

    @TempDir
    Path tempDir;

    private MetadataConfigurationService serviceFor(String configPath) {
        MetadataConfigurationService service = new MetadataConfigurationService();
        service.config = new StubFormatumConfig().withMetadataConfigPath(configPath);
        return service;
    }

    private MetadataConfigurationService startedWith(String yaml) throws IOException {
        Path configFile = tempDir.resolve("metadata-config.yml");
        Files.writeString(configFile, yaml, StandardCharsets.UTF_8);

        MetadataConfigurationService service = serviceFor(configFile.toString());
        service.onStart(null);
        return service;
    }

    @Test
    @DisplayName("A valid configuration is loaded in order and exposed through the getters")
    void loadsConfiguration() throws IOException {
        MetadataConfigurationService service = startedWith(YAML);

        assertEquals(List.of("dc.title", "dc.description.abstract"),
                service.getMetadata().stream().map(MetadataField::getField).toList());
        assertEquals(List.of("dc.title"),
                service.getMandatoryFields().stream().map(MetadataField::getField).toList());
        assertEquals("Titel/Title", service.getFieldByName("dc.title").getDisplayName());
    }

    @Test
    @DisplayName("isMandatory reflects the configuration")
    void reportsMandatoryFlag() throws IOException {
        MetadataConfigurationService service = startedWith(YAML);

        assertTrue(service.isMandatory("dc.title"));
        assertFalse(service.isMandatory("dc.description.abstract"));
    }

    @Test
    @DisplayName("isMandatory is false for an unknown field instead of failing")
    void unknownFieldIsNotMandatory() throws IOException {
        MetadataConfigurationService service = startedWith(YAML);

        assertFalse(service.isMandatory("does.not.exist"));
        assertNull(service.getFieldByName("does.not.exist"));
    }

    @Test
    @DisplayName("A missing configuration file aborts the boot and names the path")
    void missingFileAbortsStartup() {
        Path missing = tempDir.resolve("nope.yml");
        MetadataConfigurationService service = serviceFor(missing.toString());

        ConfigurationException exception =
                assertThrows(ConfigurationException.class, () -> service.onStart(null));

        assertTrue(exception.getMessage().contains(missing.toString()),
                "The message should name the unreadable path, got: " + exception.getMessage());
    }

    @Test
    @DisplayName("Malformed YAML aborts the boot")
    void malformedYamlAbortsStartup() throws IOException {
        Path configFile = tempDir.resolve("broken.yml");
        Files.writeString(configFile, "metadata: [ this is not: valid yaml", StandardCharsets.UTF_8);

        MetadataConfigurationService service = serviceFor(configFile.toString());

        assertThrows(ConfigurationException.class, () -> service.onStart(null));
    }

    @Test
    @DisplayName("An unknown key in the configuration aborts the boot")
    void unknownKeyAbortsStartup() throws IOException {
        Path configFile = tempDir.resolve("unknown-key.yml");
        Files.writeString(configFile, "somethingElse: true", StandardCharsets.UTF_8);

        MetadataConfigurationService service = serviceFor(configFile.toString());

        assertThrows(ConfigurationException.class, () -> service.onStart(null));
    }

    @Test
    @DisplayName("Current behaviour: an empty 'metadata' key fails with an NPE, "
            + "not with the descriptive ConfigurationException")
    void emptyMetadataKeyFailsUnhelpfully() throws IOException {
        Path configFile = tempDir.resolve("empty-metadata.yml");
        Files.writeString(configFile, "metadata:\n", StandardCharsets.UTF_8);

        MetadataConfigurationService service = serviceFor(configFile.toString());

        assertThrows(NullPointerException.class, () -> service.onStart(null));
    }
}
