package de.leuphana.escience.formatum.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigurationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetadataConfigurationService {

    private static final Logger logger = Logger.getLogger(MetadataConfigurationService.class.getName());

    @Inject
    FormatumConfig config;

    private MetadataConfiguration configuration;

    void onStart(@Observes StartupEvent ev) {
        String configPath = config.metadataConfigPath();

        logger.log(Level.INFO, "Loading metadata configuration from {0}", configPath);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = Files.newInputStream(Paths.get(configPath))) {
            configuration = mapper.readValue(is, MetadataConfiguration.class);
        } catch (IOException e) {
            logger.severe("Failed to load metadata configuration: " + e.getMessage());
            throw new ConfigurationException("Failed to load metadata configuration from " + configPath, e);
        }

        logger.log(Level.INFO, "Metadata configuration loaded successfully. Fields: {0}",
                   configuration.getMetadataFields().size());
    }

    public List<MetadataField> getMetadata() {
        return configuration.getMetadataFields();
    }

    public List<MetadataField> getMandatoryFields() {
        return configuration.getMandatoryFields();
    }

    public MetadataField getFieldByName(String fieldName) {
        return configuration.getFieldByName(fieldName);
    }

    public boolean isMandatory(String fieldName) {
        MetadataField field = getFieldByName(fieldName);
        return field != null && field.isMandatory();
    }
}
