package de.leuphana.escience.formatum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import de.leuphana.escience.formatum.config.StubFormatumConfig;
import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.exception.PandocConversionException;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers template resolution and the Pandoc command line. No test starts a Pandoc
 * process: the CI image that runs the build has neither pandoc nor pdflatex installed.
 */
class PandocServiceTest {

    @TempDir
    Path templatesDir;

    private StubFormatumConfig config;
    private MarkdownService markdownService;
    private PandocService service;

    @BeforeEach
    void setUp() {
        config = new StubFormatumConfig().withTemplatesPath(templatesDir.toString());
        markdownService = mock(MarkdownService.class);
        service = new PandocService();
        service.config = config;
        service.markdownService = markdownService;
    }

    private Path givenTemplate(String name) throws IOException {
        Path template = templatesDir.resolve(name + ".latex");
        Files.writeString(template, "$body$", StandardCharsets.UTF_8);
        return template;
    }

    @Test
    @DisplayName("A requested template is resolved inside the configured directory")
    void resolvesRequestedTemplate() throws IOException {
        Path template = givenTemplate("leuphana");

        Optional<Path> resolved = service.resolveTemplate("leuphana");

        assertEquals(Optional.of(template), resolved);
    }

    @Test
    @DisplayName("Without a requested name the configured default template is used")
    void fallsBackToDefaultTemplate() throws IOException {
        Path template = givenTemplate("fallback");
        config.withDefaultTemplateName("fallback");

        assertEquals(Optional.of(template), service.resolveTemplate(null));
        assertEquals(Optional.of(template), service.resolveTemplate(""));
    }

    @Test
    @DisplayName("A requested template wins over the configured default")
    void requestedTemplateWinsOverDefault() throws IOException {
        givenTemplate("fallback");
        Path requested = givenTemplate("requested");
        config.withDefaultTemplateName("fallback");

        assertEquals(Optional.of(requested), service.resolveTemplate("requested"));
    }

    @Test
    @DisplayName("Without a name and without a default, Pandoc's built-in template is used")
    void usesPandocDefaultWithoutConfiguration() {
        assertEquals(Optional.empty(), service.resolveTemplate(null));
        assertEquals(Optional.empty(), service.resolveTemplate(""));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "../../etc/passwd",
        "../leuphana",
        "foo/bar",
        "a b",
        "leuphana.latex",
        "leuphana;rm -rf /",
        "template$name"
    })
    @DisplayName("Template names outside of letters, digits, '-' and '_' are rejected")
    void rejectsInvalidTemplateNames(String name) {
        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> service.resolveTemplate(name));

        assertTrue(exception.getMessage().startsWith("Invalid template name '" + name + "'"),
                "Unexpected message: " + exception.getMessage());
    }

    @Test
    @DisplayName("A template name longer than 64 characters is rejected")
    void rejectsOverlongTemplateName() {
        String name = "a".repeat(65);

        assertThrows(MetadataValidationException.class, () -> service.resolveTemplate(name));
        assertFalse(Files.exists(templatesDir.resolve(name + ".latex")));
    }

    @Test
    @DisplayName("A template name of exactly 64 characters is still valid")
    void acceptsMaximumLengthTemplateName() throws IOException {
        String name = "a".repeat(64);
        Path template = givenTemplate(name);

        assertEquals(Optional.of(template), service.resolveTemplate(name));
    }

    @Test
    @DisplayName("A valid but unknown template name is rejected")
    void rejectsUnknownTemplate() {
        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> service.resolveTemplate("missing"));

        assertEquals("Unknown template 'missing'", exception.getMessage());
    }

    @Test
    @DisplayName("An unreadable default template is rejected as well")
    void rejectsUnknownDefaultTemplate() {
        config.withDefaultTemplateName("missing");

        assertThrows(MetadataValidationException.class, () -> service.resolveTemplate(null));
    }

    @Test
    @DisplayName("The command passes the template to Pandoc as an absolute path")
    void buildsCommandWithTemplate() throws IOException {
        Path template = givenTemplate("leuphana");
        File input = templatesDir.resolve("in.md").toFile();
        File output = templatesDir.resolve("out.pdf").toFile();

        List<String> command = service.buildCommand(Optional.of(template), input, output);

        assertEquals(List.of("pandoc", "--pdf-engine=pdflatex", "-f", "markdown+autolink_bare_uris",
                "--template", template.toAbsolutePath().toString(),
                input.getAbsolutePath(), "-s", "-o", output.getAbsolutePath()), command);
    }

    @Test
    @DisplayName("Without a template the command carries no --template argument")
    void buildsCommandWithoutTemplate() {
        File input = templatesDir.resolve("in.md").toFile();
        File output = templatesDir.resolve("out.pdf").toFile();

        List<String> command = service.buildCommand(Optional.empty(), input, output);

        assertEquals(List.of("pandoc", "--pdf-engine=pdflatex", "-f", "markdown+autolink_bare_uris",
                input.getAbsolutePath(), "-s", "-o", output.getAbsolutePath()), command);
        assertFalse(command.contains("--template"));
    }

    @Test
    @DisplayName("convert rejects an invalid template before rendering any Markdown")
    void convertValidatesTemplateBeforeDoingWork() {
        assertThrows(MetadataValidationException.class,
                () -> service.convert(JsonValue.EMPTY_JSON_OBJECT, "../../etc/passwd"));

        verifyNoInteractions(markdownService);
    }

    @Test
    @DisplayName("convert rejects an unknown template before rendering any Markdown")
    void convertRejectsUnknownTemplateBeforeDoingWork() {
        assertThrows(MetadataValidationException.class,
                () -> service.convert(JsonValue.EMPTY_JSON_OBJECT, "missing"));

        verifyNoInteractions(markdownService);
    }

    // --- temporary file handling -------------------------------------------------------

    /**
     * Stands in for the Pandoc process so that the surrounding file handling can be
     * exercised without one. It records the command and the Markdown that was handed to
     * Pandoc, both of which are gone by the time {@code convert} returns.
     */
    private static final class FakePandoc extends PandocService {

        private List<String> command;
        private String writtenMarkdown;
        private Exception failure;

        @Override
        void runPandoc(List<String> command) throws IOException, InterruptedException {
            this.command = command;
            this.writtenMarkdown = Files.readString(inputPath(), StandardCharsets.UTF_8);

            if (failure instanceof IOException ioException) {
                throw ioException;
            }
            if (failure instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            Files.writeString(outputPath(), "%PDF-1.5 generated", StandardCharsets.UTF_8);
        }

        private Path inputPath() {
            return Paths.get(command.get(command.size() - 4));
        }

        private Path outputPath() {
            return Paths.get(command.get(command.size() - 1));
        }
    }

    private FakePandoc fakePandoc(Exception failure) {
        FakePandoc fake = new FakePandoc();
        fake.config = config;
        fake.markdownService = markdownService;
        fake.failure = failure;
        when(markdownService.JsonToMarkdown(any())).thenReturn("## Titel/Title\n\nEin Titel\n\n");
        return fake;
    }

    @Test
    @DisplayName("A successful conversion returns the PDF and removes the intermediate Markdown")
    void keepsOutputAndRemovesIntermediateFile() {
        FakePandoc fake = fakePandoc(null);

        File result = fake.convert(JsonValue.EMPTY_JSON_OBJECT, null);

        assertTrue(result.exists(), "The caller needs the generated PDF");
        assertEquals("## Titel/Title\n\nEin Titel\n\n", fake.writtenMarkdown);
        assertFalse(Files.exists(fake.inputPath()), "The intermediate Markdown must be gone");

        assertTrue(result.delete());
    }

    @Test
    @DisplayName("A failed Pandoc run leaves no output file behind")
    void removesOutputFileWhenPandocFails() {
        FakePandoc fake = fakePandoc(new PandocConversionException("Pandoc exited with code 43"));

        assertThrows(PandocConversionException.class,
                () -> fake.convert(JsonValue.EMPTY_JSON_OBJECT, null));

        assertFalse(Files.exists(fake.outputPath()), "The output file must not be leaked");
        assertFalse(Files.exists(fake.inputPath()), "The intermediate Markdown must be gone");
    }

    @Test
    @DisplayName("An I/O error leaves no output file behind")
    void removesOutputFileOnIoError() {
        FakePandoc fake = fakePandoc(new IOException("pandoc not found"));

        assertThrows(PandocConversionException.class,
                () -> fake.convert(JsonValue.EMPTY_JSON_OBJECT, null));

        assertFalse(Files.exists(fake.outputPath()), "The output file must not be leaked");
    }

    @Test
    @DisplayName("An interrupted conversion leaves no output file behind and restores the flag")
    void removesOutputFileWhenInterrupted() {
        FakePandoc fake = fakePandoc(new InterruptedException("interrupted"));

        assertThrows(PandocConversionException.class,
                () -> fake.convert(JsonValue.EMPTY_JSON_OBJECT, null));

        assertFalse(Files.exists(fake.outputPath()), "The output file must not be leaked");
        assertTrue(Thread.interrupted(), "The interrupt flag must be restored for the caller");
    }
}
