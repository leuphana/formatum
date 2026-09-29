package de.leuphana.escience.formatum.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import de.leuphana.escience.formatum.config.MetadataConfigurationService;
import de.leuphana.escience.formatum.config.MetadataField;
import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.testsupport.MetadataFields;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetadataValidationServiceTest {

    private static final String YAML = """
            metadata:
              - field: "dc.title"
                mandatory: true
                displayName: "Titel/Title"
              - field: "dc.contributor.author"
                mandatory: true
                displayName: "Autor*in/Author"
              - field: "dc.description.abstract"
                mandatory: false
                displayName: "Zusammenfassung/Abstract"
            """;

    private MetadataConfigurationService configurationService;
    private MetadataValidationService service;

    @BeforeEach
    void setUp() {
        configurationService = mock(MetadataConfigurationService.class);
        service = new MetadataValidationService();
        service.metadataConfigurationService = configurationService;
        configure(YAML);
    }

    private void configure(String yaml) {
        List<MetadataField> fields = MetadataFields.fromYaml(yaml);
        when(configurationService.getMandatoryFields())
                .thenReturn(fields.stream().filter(MetadataField::isMandatory).toList());
    }

    private static JsonObject metadata(String... keys) {
        var builder = Json.createObjectBuilder();
        for (String key : keys) {
            builder.add(key, Json.createArrayBuilder().add("a value"));
        }
        return builder.build();
    }

    @Test
    @DisplayName("All mandatory fields present with a non-empty array passes")
    void acceptsCompleteMetadata() {
        JsonObject metadata = metadata("dc.title", "dc.contributor.author");

        assertDoesNotThrow(() -> service.validateMetadata(metadata));
    }

    @Test
    @DisplayName("Optional fields may be absent")
    void optionalFieldsMayBeAbsent() {
        JsonObject metadata = metadata("dc.title", "dc.contributor.author");

        assertDoesNotThrow(() -> service.validateMetadata(metadata));
        assertTrue(service.getMissingMandatoryFields(metadata).isEmpty());
    }

    @Test
    @DisplayName("A missing mandatory field is reported as 'displayName (field)'")
    void reportsMissingFieldWithDisplayNameAndKey() {
        JsonObject metadata = metadata("dc.contributor.author");

        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> service.validateMetadata(metadata));

        assertEquals("Fehlende Pflichtfelder: Titel/Title (dc.title)", exception.getMessage());
    }

    @Test
    @DisplayName("Several missing fields are joined in configuration order")
    void joinsSeveralMissingFields() {
        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> service.validateMetadata(JsonValue.EMPTY_JSON_OBJECT));

        assertEquals("Fehlende Pflichtfelder: Titel/Title (dc.title), "
                + "Autor*in/Author (dc.contributor.author)", exception.getMessage());
    }

    @Test
    @DisplayName("An empty array counts as a missing field")
    void emptyArrayCountsAsMissing() {
        JsonObject metadata = Json.createObjectBuilder()
                .add("dc.title", Json.createArrayBuilder())
                .add("dc.contributor.author", Json.createArrayBuilder().add("Doe, Jane"))
                .build();

        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> service.validateMetadata(metadata));

        assertEquals("Fehlende Pflichtfelder: Titel/Title (dc.title)", exception.getMessage());
    }

    @Test
    @DisplayName("A JSON null value counts as a missing field")
    void jsonNullCountsAsMissing() {
        JsonObject metadata = Json.createObjectBuilder()
                .addNull("dc.title")
                .add("dc.contributor.author", Json.createArrayBuilder().add("Doe, Jane"))
                .build();

        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> service.validateMetadata(metadata));

        assertEquals("Fehlende Pflichtfelder: Titel/Title (dc.title)", exception.getMessage());
    }

    @Test
    @DisplayName("A bare, non-array value counts as present")
    void bareValueCountsAsPresent() {
        JsonObject metadata = Json.createObjectBuilder()
                .add("dc.title", "A plain string")
                .add("dc.contributor.author", Json.createArrayBuilder().add("Doe, Jane"))
                .build();

        assertDoesNotThrow(() -> service.validateMetadata(metadata));
    }

    @Test
    @DisplayName("Without mandatory fields even empty metadata is valid")
    void acceptsAnythingWhenNothingIsMandatory() {
        configure("""
                metadata:
                  - field: "dc.title"
                    mandatory: false
                    displayName: "Titel/Title"
                """);

        assertDoesNotThrow(() -> service.validateMetadata(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    @DisplayName("getMissingMandatoryFields lists the display names only, without the field key")
    void listsMissingDisplayNamesOnly() {
        List<String> missing = service.getMissingMandatoryFields(JsonValue.EMPTY_JSON_OBJECT);

        assertEquals(List.of("Titel/Title", "Autor*in/Author"), missing);
    }
}
