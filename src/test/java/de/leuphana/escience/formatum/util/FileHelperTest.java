package de.leuphana.escience.formatum.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHelperTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("An existing file is deleted")
    void deletesExistingFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("doomed.pdf"), "content", StandardCharsets.UTF_8);

        FileHelper.deleteFile(file.toFile());

        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("null is ignored")
    void toleratesNull() {
        assertDoesNotThrow(() -> FileHelper.deleteFile(null));
    }

    @Test
    @DisplayName("A file that does not exist is ignored")
    void toleratesMissingFile() {
        File missing = tempDir.resolve("never-existed.pdf").toFile();

        assertDoesNotThrow(() -> FileHelper.deleteFile(missing));
    }

    @Test
    @DisplayName("A failed deletion is swallowed instead of propagated")
    void swallowsFailedDeletion() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("not-empty"));
        Files.writeString(directory.resolve("child.txt"), "content", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> FileHelper.deleteFile(directory.toFile()));

        assertTrue(Files.exists(directory), "A non-empty directory cannot be deleted");
    }
}
