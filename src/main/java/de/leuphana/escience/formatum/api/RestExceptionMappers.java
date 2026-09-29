package de.leuphana.escience.formatum.api;

import java.util.logging.Level;
import java.util.logging.Logger;

import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.exception.PandocConversionException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class RestExceptionMappers {
    private static final Logger logger = Logger.getLogger(RestExceptionMappers.class.getName());

    @ServerExceptionMapper
    public Response handleValidation(MetadataValidationException e) {
        logger.log(Level.INFO, "Validation failed: {0}", e.getMessage());
        return errorResponse(Response.Status.BAD_REQUEST, e.getMessage());
    }

    @ServerExceptionMapper
    public Response handleConversion(PandocConversionException e) {
        logger.log(Level.SEVERE, "Conversion failed", e);
        return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "PDF-Konvertierung fehlgeschlagen");
    }

    private static Response errorResponse(Response.Status status, String message) {
        JsonObject errorJson = Json.createObjectBuilder()
                .add("error", message)
                .add("status", status.getStatusCode())
                .build();

        return Response.status(status)
                .entity(errorJson)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}