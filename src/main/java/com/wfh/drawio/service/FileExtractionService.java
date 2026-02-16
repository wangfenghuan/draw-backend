package com.wfh.drawio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @Title: FileExtractionService
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2026/2/16
 * @description: File extraction service for ZIP files
 */
@Service
@Slf4j
public class FileExtractionService {

    private static final String PROJECT_TMP_DIR = "tmp/code-uploads";
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    /**
     * Extract uploaded ZIP file to project tmp directory
     *
     * @param file Uploaded ZIP file
     * @return Path to extracted directory
     * @throws IOException If extraction fails
     */
    public Path extractZipFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Uploaded file is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("File size exceeds maximum limit of 100MB");
        }

        // Create tmp directory in project root
        Path projectTmpDir = Paths.get(PROJECT_TMP_DIR);
        if (!Files.exists(projectTmpDir)) {
            Files.createDirectories(projectTmpDir);
            log.info("Created project tmp directory: {}", projectTmpDir.toAbsolutePath());
        }

        // Create unique directory with timestamp
        String timestamp = String.valueOf(System.currentTimeMillis());
        String originalFilename = file.getOriginalFilename();
        String dirName = originalFilename != null ? 
                originalFilename.replace(".zip", "") + "-" + timestamp : 
                "upload-" + timestamp;
        
        Path extractDir = projectTmpDir.resolve(dirName);
        Files.createDirectories(extractDir);
        log.info("Created extraction directory: {}", extractDir.toAbsolutePath());

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = extractDir.resolve(entry.getName());

                // Security check: prevent path traversal
                if (!filePath.normalize().startsWith(extractDir.normalize())) {
                    throw new IOException("Invalid ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    // Create parent directories if needed
                    Files.createDirectories(filePath.getParent());
                    
                    // Extract file
                    try (OutputStream os = Files.newOutputStream(filePath)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            // Clean up on error
            deleteDirectory(extractDir);
            throw e;
        }

        log.info("Successfully extracted ZIP file to: {}", extractDir.toAbsolutePath());
        return extractDir;
    }

    /**
     * Delete directory and all its contents
     *
     * @param directory Directory to delete
     */
    public void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
                log.info("Deleted temporary directory: {}", directory);
            }
        } catch (IOException e) {
            log.error("Error deleting directory: {}", directory, e);
        }
    }
}
