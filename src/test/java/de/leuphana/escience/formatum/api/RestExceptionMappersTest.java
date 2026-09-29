package de.leuphana.escience.formatum.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.exception.PandocConversionException;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RestExceptionMappersTest {

    private final RestExceptionMappers mappers = new RestExceptionMappers();

    @Test
    @DisplayName("A validation error becomes a 400 carrying the original message")
    void mapsValidationErrorToBadRequest() {
        Response response = mappers.handleValidation(
                new MetadataValidationException("Fehlende Pflichtfelder: Titel/Title (dc.title)"));

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

        JsonObject body = (JsonObject) response.getEntity();
        assertEquals("Fehlende Pflichtfelder: Titel/Title (dc.title)", body.getString("error"));
        assertEquals(400, body.getInt("status"));
    }

    @Test
    @DisplayName("A conversion error becomes a 500 that does not leak internals")
    void mapsConversionErrorToServerError() {
        Response response = mappers.handleConversion(
                new PandocConversionException("Pandoc exited with code 43 at /tmp/outputFile123.pdf"));

        assertEquals(500, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

        JsonObject body = (JsonObject) response.getEntity();
        assertEquals("PDF-Konvertierung fehlgeschlagen", body.getString("error"));
        assertEquals(500, body.getInt("status"));
        assertFalse(body.toString().contains("/tmp/outputFile123.pdf"),
                "Internal details must not reach the client");
    }
}
