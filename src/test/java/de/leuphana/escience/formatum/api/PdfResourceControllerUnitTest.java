package de.leuphana.escience.formatum.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.service.MetadataValidationService;
import de.leuphana.escience.formatum.service.PandocService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Drives the controller directly so that the {@link StreamingOutput} - and with it the
 * promise that no generated file survives the response - can be inspected. Over HTTP the
 * stream is consumed by the server before a test could look at it.
 */
class PdfResourceControllerUnitTest {

    @TempDir
    Path tempDir;

    private PandocService pandocService;
    private MetadataValidationService validationService;
    private PdfResourceController controller;

    @BeforeEach
    void setUp() {
        pandocService = mock(PandocService.class);
        validationService = mock(MetadataValidationService.class);
        controller = new PdfResourceController();
        controller.pandocService = pandocService;
        controller.validationService = validationService;
    }

    private static JsonObject json(String raw) {
        try (JsonReader reader = Json.createReader(new StringReader(raw))) {
            return reader.readObject();
        }
    }

    private Path givenGeneratedPdf() throws IOException {
        Path pdf = tempDir.resolve("generated.pdf");
        Files.write(pdf, "%PDF-1.5 content".getBytes(StandardCharsets.UTF_8));
        when(pandocService.convert(any(), any())).thenReturn(pdf.toFile());
        return pdf;
    }

    @Test
    @DisplayName("A valid request answers 200 with the PDF attachment header")
    void respondsWithPdfAttachment() throws IOException {
        givenGeneratedPdf();

        Response response = controller.convertToDocument(json("""
                {"template": "leuphana", "metadata": {"dc.title": ["Ein Titel"]}}
                """));

        assertEquals(200, response.getStatus());
        assertEquals("attachment; filename=\"metadata-summary.pdf\"",
                response.getHeaderString("Content-Disposition"));
    }

    @Test
    @DisplayName("The generated file is streamed and then deleted")
    void streamsAndDeletesGeneratedFile() throws IOException {
        Path pdf = givenGeneratedPdf();

        Response response = controller.convertToDocument(json("""
                {"metadata": {"dc.title": ["Ein Titel"]}}
                """));

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(sink);

        assertArrayEquals("%PDF-1.5 content".getBytes(StandardCharsets.UTF_8), sink.toByteArray());
        assertFalse(Files.exists(pdf), "Nothing may be persisted after the response");
    }

    @Test
    @DisplayName("The generated file is deleted even when streaming fails")
    void deletesGeneratedFileWhenStreamingFails() throws IOException {
        Path pdf = givenGeneratedPdf();

        Response response = controller.convertToDocument(json("""
                {"metadata": {"dc.title": ["Ein Titel"]}}
                """));

        StreamingOutput stream = (StreamingOutput) response.getEntity();
        OutputStream brokenClient = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("client gone");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("client gone");
            }
        };

        assertThrows(IOException.class, () -> stream.write(brokenClient));

        assertFalse(Files.exists(pdf), "A failed response must not leave the file behind");
    }

    @Test
    @DisplayName("The template name is passed through unchanged")
    void passesTemplateNameThrough() throws IOException {
        givenGeneratedPdf();

        controller.convertToDocument(json("""
                {"template": "leuphana", "metadata": {"dc.title": ["Ein Titel"]}}
                """));

        ArgumentCaptor<String> template = ArgumentCaptor.forClass(String.class);
        verify(pandocService).convert(any(), template.capture());
        assertEquals("leuphana", template.getValue());
    }

    @Test
    @DisplayName("A request without a template asks for the default by passing null")
    void passesNullWithoutTemplate() throws IOException {
        givenGeneratedPdf();

        controller.convertToDocument(json("""
                {"metadata": {"dc.title": ["Ein Titel"]}}
                """));

        ArgumentCaptor<String> template = ArgumentCaptor.forClass(String.class);
        verify(pandocService).convert(any(), template.capture());
        assertNull(template.getValue());
    }

    @Test
    @DisplayName("Only the inner metadata object reaches validation and conversion")
    void forwardsInnerMetadataObject() throws IOException {
        givenGeneratedPdf();
        JsonObject inner = json("""
                {"dc.title": ["Ein Titel"]}
                """);

        controller.convertToDocument(json("""
                {"template": "leuphana", "metadata": {"dc.title": ["Ein Titel"]}}
                """));

        verify(validationService).validateMetadata(inner);
        verify(pandocService).convert(eq(inner), any());
    }

    @Test
    @DisplayName("A missing 'metadata' object is rejected before anything else happens")
    void rejectsMissingMetadataObject() {
        MetadataValidationException exception = assertThrows(MetadataValidationException.class,
                () -> controller.convertToDocument(json("""
                        {"template": "leuphana"}
                        """)));

        assertEquals("Fehlendes 'metadata' Objekt in der Anfrage", exception.getMessage());
        verifyNoInteractions(validationService, pandocService);
    }

    @Test
    @DisplayName("A failed validation stops the request before Pandoc is called")
    void doesNotConvertWhenValidationFails() {
        doThrow(new MetadataValidationException("Fehlende Pflichtfelder: Titel/Title (dc.title)"))
                .when(validationService).validateMetadata(any());

        assertThrows(MetadataValidationException.class,
                () -> controller.convertToDocument(json("""
                        {"metadata": {}}
                        """)));

        verifyNoInteractions(pandocService);
    }
}
