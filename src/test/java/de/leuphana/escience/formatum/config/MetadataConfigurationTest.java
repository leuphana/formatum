package de.leuphana.escience.formatum.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import de.leuphana.escience.formatum.testsupport.MetadataFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetadataConfigurationTest {

    private static final String YAML = """
            metadata:
              - field: "dc.title"
                mandatory: true
                displayName: "Titel/Title"
              - field: "dc.description.abstract"
                mandatory: false
                displayName: "Zusammenfassung/Abstract"
              - field: "dc.contributor.author"
                mandatory: true
                displayName: "Autor*in/Author"
            """;

    @Test
    @DisplayName("YAML is mapped onto field, mandatory and displayName")
    void mapsAllProperties() {
        List<MetadataField> fields = MetadataFields.fromYaml(YAML);

        assertEquals(3, fields.size());
        assertEquals("dc.title", fields.get(0).getField());
        assertEquals("Titel/Title", fields.get(0).getDisplayName());
        assertTrue(fields.get(0).isMandatory());
        assertFalse(fields.get(1).isMandatory());
    }

    @Test
    @DisplayName("An omitted mandatory flag defaults to false")
    void mandatoryDefaultsToFalse() {
        MetadataField field = MetadataFields.fromYaml("""
                metadata:
                  - field: "dc.title"
                    displayName: "Titel/Title"
                """).get(0);

        assertFalse(field.isMandatory());
    }

    @Test
    @DisplayName("The list keeps the configuration order, which drives the document order")
    void keepsConfigurationOrder() {
        List<MetadataField> fields = MetadataFields.fromYaml(YAML);

        assertEquals(List.of("dc.title", "dc.description.abstract", "dc.contributor.author"),
                fields.stream().map(MetadataField::getField).toList());
    }

    @Test
    @DisplayName("getMandatoryFields returns only mandatory fields, in configuration order")
    void returnsMandatoryFieldsInOrder() {
        MetadataConfiguration configuration = MetadataFields.configurationFromYaml(YAML);

        assertEquals(List.of("dc.title", "dc.contributor.author"),
                configuration.getMandatoryFields().stream().map(MetadataField::getField).toList());
    }

    @Test
    @DisplayName("getMandatoryFields returns an empty list when nothing is mandatory")
    void returnsEmptyListWithoutMandatoryFields() {
        MetadataConfiguration configuration = MetadataFields.configurationFromYaml("""
                metadata:
                  - field: "dc.title"
                    mandatory: false
                    displayName: "Titel/Title"
                """);

        assertTrue(configuration.getMandatoryFields().isEmpty());
    }

    @Test
    @DisplayName("getFieldByName finds a configured field")
    void findsFieldByName() {
        MetadataConfiguration configuration = MetadataFields.configurationFromYaml(YAML);

        MetadataField field = configuration.getFieldByName("dc.contributor.author");

        assertEquals("Autor*in/Author", field.getDisplayName());
    }

    @Test
    @DisplayName("getFieldByName returns null for an unknown field")
    void returnsNullForUnknownField() {
        MetadataConfiguration configuration = MetadataFields.configurationFromYaml(YAML);

        assertNull(configuration.getFieldByName("does.not.exist"));
        assertNull(configuration.getFieldByName(null));
    }

    @Test
    @DisplayName("toString names field and mandatory flag")
    void toStringContainsFieldAndMandatory() {
        MetadataField field = MetadataFields.field("dc.title", true, "Titel/Title");

        assertEquals("MetadataField[field=dc.title, mandatory=true]", field.toString());
    }
}
