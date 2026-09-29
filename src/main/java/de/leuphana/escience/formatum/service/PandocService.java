package de.leuphana.escience.formatum.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.leuphana.escience.formatum.config.FormatumConfig;
import de.leuphana.escience.formatum.exception.MetadataValidationException;
import de.leuphana.escience.formatum.exception.PandocConversionException;
import de.leuphana.escience.formatum.util.FileHelper;
import io.quarkus.runtime.util.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ApplicationScoped
public class PandocService {
    private static final Pattern VALID_TEMPLATE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    Logger logger = Logger.getLogger(PandocService.class.getName());

    @Inject
    FormatumConfig config;

    @Inject
    MarkdownService markdownService;

    public File convert(JsonObject metadata, String templateName) {
        File outputFile = null;
        File tempFile = null;
        boolean converted = false;

        try {
            Optional<Path> templateFile = resolveTemplate(templateName);

            tempFile = Files.createTempFile("intermediateFile", ".md").toFile();
            String yaml = markdownService.JsonToMarkdown(metadata);

            Files.writeString(tempFile.toPath(), yaml, StandardCharsets.UTF_8);

            outputFile = Files.createTempFile("outputFile", ".pdf").toFile();

            runPandoc(buildCommand(templateFile, tempFile, outputFile));
            converted = true;
        } catch (IOException e) {
            throw new PandocConversionException("Failed to convert metadata to PDF", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PandocConversionException("Interrupted while waiting for Pandoc", e);
        } finally {
            FileHelper.deleteFile(tempFile);
            if (!converted) {
                FileHelper.deleteFile(outputFile);
            }
        }
        return outputFile;
    }

    void runPandoc(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        process.waitFor();

        if (process.exitValue() != 0) {
            logger.log(Level.SEVERE, "Pandoc failed with output: {0}", output);
            throw new PandocConversionException("Pandoc exited with code " + process.exitValue());
        }
    }

    List<String> buildCommand(Optional<Path> templateFile, File inputFile, File outputFile) {
        // autolink_bare_uris: Adressen stehen in den Metadaten als blosser Text
        // (z. B. hinter einem Akteursnamen). Ohne diese Erweiterung liest Pandoc
        // sie als Fliesstext, und im PDF ist die Adresse weder anklickbar noch an
        // einer sinnvollen Stelle umbrechbar. Als Link kann das Template sie
        // umbrechen, ohne dass das Ziel verloren geht (siehe \UrlBreaks dort).
        List<String> command = new ArrayList<>(List.of(
            "pandoc", "--pdf-engine=pdflatex", "-f", "markdown+autolink_bare_uris"));
        templateFile.ifPresent(template -> command.addAll(
            List.of("--template", template.toAbsolutePath().toString())));
        command.addAll(List.of(inputFile.getAbsolutePath(), "-s", "-o", outputFile.getAbsolutePath()));
        return command;
    }

    Optional<Path> resolveTemplate(String requestedName) {
        String templateName = StringUtil.isNullOrEmpty(requestedName)
            ? config.defaultTemplateName().orElse(null)
            : requestedName;

        if (StringUtil.isNullOrEmpty(templateName)) {
            logger.log(Level.INFO, "No template requested, using pandoc's built-in default template");
            return Optional.empty();
        }

        if (!VALID_TEMPLATE_NAME.matcher(templateName).matches()) {
            throw new MetadataValidationException("Invalid template name '" + templateName
                + "'. Allowed are letters, digits, '-' and '_'.");
        }

        Path templatePath = Paths.get(config.templatesPath(), templateName + ".latex");
        if (!Files.isReadable(templatePath)) {
            throw new MetadataValidationException("Unknown template '" + templateName + "'");
        }

        logger.log(Level.INFO, "Using template {0}", templatePath);
        return Optional.of(templatePath);
    }
}