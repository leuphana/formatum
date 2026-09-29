package de.leuphana.escience.formatum.api;

import java.io.File;
import java.nio.file.Files;

import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.service.MetadataValidationService;
import de.leuphana.escience.formatum.service.PandocService;
import de.leuphana.escience.formatum.util.FileHelper;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/api/documents")
public class PdfResourceController {
    @Inject
    PandocService pandocService;

    @Inject
    MetadataValidationService validationService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response convertToDocument(JsonObject metadata) {
        if (!metadata.containsKey("metadata")) {
            throw new MetadataValidationException("Fehlendes 'metadata' Objekt in der Anfrage");
        }

        JsonObject metadataJson = metadata.getJsonObject("metadata");
        String templateName = metadata.getString("template", null);

        validationService.validateMetadata(metadataJson);

        final File finalFile = pandocService.convert(metadataJson, templateName);
        StreamingOutput stream = output -> {
            try {
                Files.copy(finalFile.toPath(), output);
            } finally {
                FileHelper.deleteFile(finalFile);
            }
        };
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"metadata-summary.pdf\"")
                .build();
    }
}