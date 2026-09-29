package de.leuphana.escience.formatum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.List;

import de.leuphana.escience.formatum.config.MetadataConfigurationService;
import de.leuphana.escience.formatum.testsupport.MetadataFields;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rendering rules are the heart of the service: section order, headings and the
 * bullet-list-vs-paragraph decision are driven entirely by the configuration.
 */
class MarkdownServiceTest {

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
    private MarkdownService service;

    @BeforeEach
    void setUp() {
        configurationService = mock(MetadataConfigurationService.class);
        service = new MarkdownService();
        service.metadataConfigurationService = configurationService;
        configure(YAML);
    }

    private void configure(String yaml) {
        when(configurationService.getMetadata()).thenReturn(MetadataFields.fromYaml(yaml));
    }

    private static JsonObject json(String raw) {
        try (JsonReader reader = Json.createReader(new StringReader(raw))) {
            return reader.readObject();
        }
    }

    @Test
    @DisplayName("A single value is rendered as a paragraph, without a bullet")
    void rendersSingleValueAsParagraph() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": ["Ein Titel"]}
                """));

        assertEquals("## Titel/Title\n\nEin Titel\n\n", markdown);
    }

    @Test
    @DisplayName("Several values are rendered as a bullet list")
    void rendersSeveralValuesAsBulletList() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.contributor.author": ["Doe, Jane", "Roe, Richard"]}
                """));

        assertEquals("## Autor*in/Author\n\n- Doe, Jane\n- Roe, Richard\n\n", markdown);
    }

    @Test
    @DisplayName("Sections follow the configuration order, not the order of the request")
    void followsConfigurationOrder() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.description.abstract": ["Kurzfassung"],
                 "dc.contributor.author": ["Doe, Jane"],
                 "dc.title": ["Ein Titel"]}
                """));

        assertEquals("## Titel/Title\n\nEin Titel\n\n"
                + "## Autor*in/Author\n\nDoe, Jane\n\n"
                + "## Zusammenfassung/Abstract\n\nKurzfassung\n\n", markdown);
    }

    @Test
    @DisplayName("Fields absent from the configuration are ignored")
    void ignoresUnconfiguredFields() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": ["Ein Titel"], "local.notConfigured": ["Unsichtbar"]}
                """));

        assertEquals("## Titel/Title\n\nEin Titel\n\n", markdown);
        assertFalse(markdown.contains("Unsichtbar"));
    }

    @Test
    @DisplayName("Configured fields absent from the request produce no section")
    void skipsFieldsMissingFromTheRequest() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.contributor.author": ["Doe, Jane"]}
                """));

        assertEquals("## Autor*in/Author\n\nDoe, Jane\n\n", markdown);
    }

    @Test
    @DisplayName("An object entry is unwrapped through its 'value' key")
    void unwrapsObjectValues() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": [{"value": "Ein Titel", "language": "de"}]}
                """));

        assertEquals("## Titel/Title\n\nEin Titel\n\n", markdown);
    }

    @Test
    @DisplayName("An object without a 'value' key falls back to its JSON representation")
    void fallsBackToJsonRepresentation() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": [{"language": "de"}]}
                """));

        assertEquals("## Titel/Title\n\n{\"language\":\"de\"}\n\n", markdown);
    }

    @Test
    @DisplayName("Non-string entries fall back to their JSON representation")
    void rendersNonStringEntries() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": [42]}
                """));

        assertEquals("## Titel/Title\n\n42\n\n", markdown);
    }

    @Test
    @DisplayName("An empty configuration yields an empty document")
    void emptyConfigurationYieldsEmptyDocument() {
        when(configurationService.getMetadata()).thenReturn(List.of());

        assertEquals("", service.JsonToMarkdown(json("""
                {"dc.title": ["Ein Titel"]}
                """)));
    }

    @Test
    @DisplayName("Empty metadata yields an empty document")
    void emptyMetadataYieldsEmptyDocument() {
        assertEquals("", service.JsonToMarkdown(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    @DisplayName("Special characters and line breaks are passed through unchanged")
    void passesSpecialCharactersThrough() {
        String value = "»Nachhaltigkeit« – 10 Jahre\nzweite Zeile & äöüß";

        String markdown = service.JsonToMarkdown(Json.createObjectBuilder()
                .add("dc.title", Json.createArrayBuilder().add(value))
                .build());

        assertEquals("## Titel/Title\n\n" + value + "\n\n", markdown);
        assertTrue(markdown.contains(" "), "The thin space must survive rendering");
    }

    @Test
    @DisplayName("A field whose only value is a hyphen is left out entirely, heading included")
    void placeholderOnlyFieldIsSkipped() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": ["Ein Titel"], "dc.contributor.author": ["-"]}
                """));

        assertEquals("## Titel/Title\n\nEin Titel\n\n", markdown);
        assertFalse(markdown.contains("Autor*in/Author"));
    }

    @Test
    @DisplayName("Surrounding whitespace does not stop a hyphen from counting as a placeholder")
    void placeholderIsRecognisedDespiteWhitespace() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.contributor.author": ["  -  "]}
                """));

        assertEquals("", markdown);
    }

    @Test
    @DisplayName("Placeholders are dropped from a multi-value field; a single survivor stays inline")
    void placeholdersAreDroppedFromMultiValueField() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.contributor.author": ["-", "Doe, Jane"]}
                """));

        assertEquals("## Autor*in/Author\n\nDoe, Jane\n\n", markdown);
    }

    @Test
    @DisplayName("A hyphen inside a longer value is kept")
    void hyphenInsideValueIsKept() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": ["Nord - Sued"]}
                """));

        assertEquals("## Titel/Title\n\nNord - Sued\n\n", markdown);
    }

    @Test
    @DisplayName("An empty array produces a heading without content")
    void emptyArrayProducesEmptySection() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": []}
                """));

        assertEquals("## Titel/Title\n\n\n", markdown);
    }

    @Test
    @DisplayName("Current behaviour: a bare, non-array value produces a heading but no content, "
            + "even though MetadataValidationService accepts the same value as present")
    void bareValueProducesEmptySection() {
        String markdown = service.JsonToMarkdown(json("""
                {"dc.title": "Ein Titel"}
                """));

        assertEquals("## Titel/Title\n\n\n", markdown);
        assertFalse(markdown.contains("Ein Titel"));
    }
}
