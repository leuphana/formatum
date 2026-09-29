package de.leuphana.escience.formatum.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.exception.PandocConversionException;
import de.leuphana.escience.formatum.service.MetadataValidationService;
import de.leuphana.escience.formatum.service.PandocService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The only test that boots the application. It verifies the wiring - RESTEasy picking up
 * {@link RestExceptionMappers}, JSON binding and the status codes documented in the
 * README. {@link PandocService} is mocked, so no Pandoc process is needed; the CI image
 * that runs the build does not have one.
 */
@QuarkusTest
class PdfResourceControllerTest {

    private static final String VALID_REQUEST = """
            {"template": "test",
             "metadata": {"dc.title": ["Ein Titel"],
                          "dc.contributor.author": ["Doe, Jane"]}}
            """;

    @InjectMock
    PandocService pandocService;

    @InjectMock
    MetadataValidationService validationService;

    private void givenGeneratedPdf() throws IOException {
        Path pdf = Files.createTempFile("formatum-test", ".pdf");
        Files.write(pdf, "%PDF-1.5 test".getBytes(StandardCharsets.UTF_8));
        when(pandocService.convert(any(), any())).thenReturn(pdf.toFile());
    }

    @Test
    @DisplayName("A valid request answers 200 with the PDF as an attachment")
    void returnsPdf() throws IOException {
        givenGeneratedPdf();

        given().contentType(ContentType.JSON).body(VALID_REQUEST)
                .when().post("/api/documents")
                .then()
                .statusCode(200)
                .header("Content-Disposition", "attachment; filename=\"metadata-summary.pdf\"")
                .body(is("%PDF-1.5 test"));
    }

    @Test
    @DisplayName("A request without a 'metadata' object answers 400")
    void rejectsMissingMetadataObject() {
        given().contentType(ContentType.JSON).body("""
                {"template": "test"}
                """)
                .when().post("/api/documents")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Fehlendes 'metadata' Objekt in der Anfrage"))
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("Missing mandatory fields answer 400 with the validation message")
    void reportsMissingMandatoryFields() {
        doThrow(new MetadataValidationException("Fehlende Pflichtfelder: Titel/Title (dc.title)"))
                .when(validationService).validateMetadata(any());

        given().contentType(ContentType.JSON).body("""
                {"metadata": {}}
                """)
                .when().post("/api/documents")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Fehlende Pflichtfelder: Titel/Title (dc.title)"))
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("An invalid template name answers 400")
    void rejectsInvalidTemplateName() {
        when(pandocService.convert(any(), any()))
                .thenThrow(new MetadataValidationException("Unknown template 'nope'"));

        given().contentType(ContentType.JSON).body("""
                {"template": "nope", "metadata": {"dc.title": ["Ein Titel"]}}
                """)
                .when().post("/api/documents")
                .then()
                .statusCode(400)
                .body("error", equalTo("Unknown template 'nope'"));
    }

    @Test
    @DisplayName("A failed conversion answers 500 without leaking internals")
    void hidesConversionDetails() {
        when(pandocService.convert(any(), any()))
                .thenThrow(new PandocConversionException("Pandoc exited with code 43"));

        given().contentType(ContentType.JSON).body(VALID_REQUEST)
                .when().post("/api/documents")
                .then()
                .statusCode(500)
                .contentType(ContentType.JSON)
                .body("error", equalTo("PDF-Konvertierung fehlgeschlagen"))
                .body("status", equalTo(500))
                .body(not(containsString("exited with code")));
    }
}
