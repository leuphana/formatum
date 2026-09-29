package de.leuphana.escience.formatum.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RegisterForReflection
public class MetadataConfiguration {
    @JsonProperty("metadata")
    private List<MetadataField> metadataFields;

    public List<MetadataField> getMetadataFields() {
        return metadataFields;
    }

    public MetadataField getFieldByName(String fieldName) {
        return metadataFields.stream()
                .filter(mf -> mf.getField().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    public List<MetadataField> getMandatoryFields() {
        return metadataFields.stream()
                .filter(MetadataField::isMandatory)
                .collect(Collectors.toList());
    }
}
