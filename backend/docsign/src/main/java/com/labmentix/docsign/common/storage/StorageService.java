package com.labmentix.docsign.common.storage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction layer over MinIO (S3-compatible) object storage.
 * All document operations go through this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;

    @Value("${app.minio.bucket-name}")
    private String bucketName;

    // ── Bucket initialisation ──────────────────────────────────

    /**
     * Ensures the configured bucket exists. Called on startup via MinioConfig.
     */
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO bucket '{}' created", bucketName);
            } else {
                log.debug("MinIO bucket '{}' already exists", bucketName);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to verify/create MinIO bucket: " + bucketName, e);
        }
    }

    // ── Upload ─────────────────────────────────────────────────

    /**
     * Uploads raw bytes to storage.
     *
     * @param objectKey   full path within the bucket (e.g. "documents/uuid/v1/file.pdf")
     * @param data        file bytes
     * @param contentType MIME type
     * @return the objectKey on success
     */
    public String upload(String objectKey, byte[] data, String contentType) {
        try (InputStream is = new ByteArrayInputStream(data)) {
            return upload(objectKey, is, data.length, contentType);
        } catch (IOException e) {
            throw new StorageException("Failed to read upload bytes", e);
        }
    }

    /**
     * Uploads a stream to storage (memory-efficient for large files).
     */
    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(stream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            log.debug("Uploaded object: {}/{}", bucketName, objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Upload failed for key: " + objectKey, e);
        }
    }

    // ── Download ───────────────────────────────────────────────

    /**
     * Downloads an object and returns its bytes.
     * For large files prefer {@link #openStream(String)}.
     */
    public byte[] download(String objectKey) {
        try (InputStream stream = openStream(objectKey)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new StorageException("Download read failed for key: " + objectKey, e);
        }
    }

    /**
     * Opens a streaming connection to an object. Caller must close the stream.
     */
    public InputStream openStream(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new StorageException("Object not found: " + objectKey, e);
            }
            throw new StorageException("Download failed for key: " + objectKey, e);
        } catch (Exception e) {
            throw new StorageException("Download failed for key: " + objectKey, e);
        }
    }

    // ── Delete ─────────────────────────────────────────────────

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.debug("Deleted object: {}/{}", bucketName, objectKey);
        } catch (Exception e) {
            log.warn("Could not delete object {}: {}", objectKey, e.getMessage());
        }
    }

    // ── Presigned URL ──────────────────────────────────────────

    /**
     * Generates a time-limited presigned GET URL.
     * Useful for direct browser downloads without routing bytes through the app.
     *
     * @param objectKey     storage key
     * @param expiryMinutes how long the URL stays valid
     */
    public String generatePresignedUrl(String objectKey, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to generate presigned URL for: " + objectKey, e);
        }
    }

    // ── Key builders ───────────────────────────────────────────

    /**
     * Builds a canonical S3 key for a document version.
     * Pattern: documents/{documentId}/v{version}/{uuid}-{filename}
     */
    public static String buildDocumentKey(UUID documentId, int version, String filename) {
        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "documents/%s/v%d/%s-%s".formatted(documentId, version, UUID.randomUUID(), safeName);
    }

    /**
     * Builds a key for the finalized, sealed PDF.
     */
    public static String buildSignedKey(UUID documentId) {
        return "documents/%s/signed/final-%s.pdf".formatted(documentId, UUID.randomUUID());
    }
}