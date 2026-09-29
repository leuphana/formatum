package de.leuphana.escience.formatum.util;

import java.io.File;
import java.util.logging.Logger;

public class FileHelper {
    private static final Logger logger = Logger.getLogger(FileHelper.class.getName());

    FileHelper() {
        // Default constructor
    }

    public static void deleteFile(File file) {
        if (file != null && file.exists()) {
            try {
                if (!file.delete()) {
                    logger.warning("Could not delete file " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.warning("Could not delete file " + file.getAbsolutePath());
            }
        }
    }
}