package de.leuphana.escience.formatum.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.leuphana.escience.formatum.config.MetadataConfigurationService;
import de.leuphana.escience.formatum.config.MetadataField;
import de.leuphana.escience.formatum.exception.MetadataValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

@ApplicationScoped
public class MetadataValidationService {
    Logger logger = Logger.getLogger(MetadataValidationService.class.getName());

    @Inject
    MetadataConfigurationService metadataConfigurationService;

    public void validateMetadata(JsonObject metadataJson) {
        List<String> missingFields = new ArrayList<>();

        for (MetadataField field : metadataConfigurationService.getMandatoryFields()) {
            String fieldName = field.getField();

            if (!isFieldPresent(metadataJson, fieldName)) {
                missingFields.add(field.getDisplayName() + " (" + fieldName + ")");
            }
        }

        if (!missingFields.isEmpty()) {
            String errorMsg = "Fehlende Pflichtfelder: " + String.join(", ", missingFields);
            logger.warning(errorMsg);
            throw new MetadataValidationException(errorMsg);
        }

        logger.log(Level.INFO, "Metadata validation successful");
    }

    private boolean isFieldPresent(JsonObject metadataJson, String fieldName) {
        if (!metadataJson.containsKey(fieldName) || metadataJson.isNull(fieldName)) {
            return false;
        }

        if (metadataJson.get(fieldName) instanceof JsonArray) {
            JsonArray array = metadataJson.getJsonArray(fieldName);
            return !array.isEmpty();
        }

        return true;
    }

    public List<String> getMissingMandatoryFields(JsonObject metadataJson) {
        List<String> missingFields = new ArrayList<>();

        for (MetadataField field : metadataConfigurationService.getMandatoryFields()) {
            String fieldName = field.getField();

            if (!isFieldPresent(metadataJson, fieldName)) {
                missingFields.add(field.getDisplayName());
            }
        }

        return missingFields;
    }
}
