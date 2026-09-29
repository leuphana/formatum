package de.leuphana.escience.formatum.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;

@RegisterForReflection
public class MetadataField {

    @JsonProperty("field")
    private String field;

    @JsonProperty("mandatory")
    private boolean mandatory;

    @JsonProperty("displayName")
    private String displayName;

    public String getField() {
        return field;
    }

    public String getDisplayName() {
        return displayName;
    }
    public boolean isMandatory() {
        return mandatory;
    }

    @Override
    public String toString() {
        return String.format("MetadataField[field=%s, mandatory=%s]",
                field, mandatory);
    }
}