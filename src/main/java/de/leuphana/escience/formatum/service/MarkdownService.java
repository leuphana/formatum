package de.leuphana.escience.formatum.service;

import de.leuphana.escience.formatum.config.MetadataConfigurationService;
import de.leuphana.escience.formatum.config.MetadataField;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MarkdownService {
    Logger logger = Logger.getLogger(MarkdownService.class.getName());

    @Inject
    MetadataConfigurationService metadataConfigurationService;

    String JsonToMarkdown(JsonObject json) {
        String NEWLINE = "\n";
        StringBuilder sb = new StringBuilder();
        List<MetadataField> sortedFields = metadataConfigurationService.getMetadata();

        for (MetadataField field : sortedFields) {
            String fieldName = field.getField();

            if (json.containsKey(fieldName)) {
                JsonValue value = json.get(fieldName);
                String displayName = field.getDisplayName();

                boolean hadValues = false;
                List<String> texts = new ArrayList<>();
                if (value instanceof JsonArray) {
                    JsonArray jsonArray = value.asJsonArray();
                    hadValues = !jsonArray.isEmpty();
                    for (JsonValue jsonValue : jsonArray) {
                        String text = getJsonValueText(jsonValue);
                        if (isPlaceholder(text)) {
                            continue;
                        }
                        texts.add(text);
                    }
                }
                if (hadValues && texts.isEmpty()) {
                    logger.log(Level.FINE, "Field {0} only holds placeholders - skipped", fieldName);
                    continue;
                }

                sb.append("## ").append(displayName).append(NEWLINE);
                for (String text : texts) {
                    if (texts.size() > 1) {
                        sb.append(NEWLINE).append("- ").append(text);
                    } else {
                        sb.append(NEWLINE).append(text);
                    }
                }
                sb.append(NEWLINE).append(NEWLINE);
            }
        }
        return sb.toString();
    }
    
    private boolean isPlaceholder(String text) {
        return text != null && "-".equals(text.strip());
    }

    private String getJsonValueText(JsonValue jsonValue) {
        if (jsonValue instanceof JsonString jsonString) {
            return jsonString.getString();
        }

        if (jsonValue.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject jsonObject = jsonValue.asJsonObject();
            if (jsonObject.containsKey("value")) {
                return jsonObject.getString("value", "");
            }
        }

        return jsonValue.toString();
    }
}
