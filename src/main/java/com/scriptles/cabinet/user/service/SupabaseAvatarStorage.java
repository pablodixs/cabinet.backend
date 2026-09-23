package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class SupabaseAvatarStorage {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final RestClient restClient;
    private final String projectUrl;
    private final String serviceKey;
    private final String bucket;

    public SupabaseAvatarStorage(
            RestClient.Builder restClientBuilder,
            @Value("${supabase.url:}") String projectUrl,
            @Value("${supabase.service-role-key:}") String serviceKey,
            @Value("${supabase.avatars-bucket:avatars}") String bucket
    ) {
        this.restClient = restClientBuilder.build();
        this.projectUrl = projectUrl == null ? "" : projectUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
        this.bucket = bucket;
    }

    public String upload(UUID userId, byte[] image, String contentType) {
        if (projectUrl.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_STORAGE_UNAVAILABLE",
                    "O armazenamento de fotos não está configurado");
        }
        if (image == null || image.length == 0 || image.length > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_SIZE",
                    "A foto deve ter até 5 MB");
        }

        MediaType mediaType = switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "image/png" -> MediaType.IMAGE_PNG;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_TYPE",
                    "Use uma imagem JPEG ou PNG");
        };

        String extension = mediaType.equals(MediaType.IMAGE_PNG) ? "png" : "jpg";
        String objectPath = userId + "/" + UUID.randomUUID() + "." + extension;
        String encodedBucket = UriUtils.encodePathSegment(bucket, StandardCharsets.UTF_8);
        String encodedPath = UriUtils.encodePath(objectPath, StandardCharsets.UTF_8);

        try {
            restClient.post()
                    .uri(projectUrl + "/storage/v1/object/" + encodedBucket + "/" + encodedPath)
                    .header("apikey", serviceKey)
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("x-upsert", "false")
                    .contentType(mediaType)
                    .body(image)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AVATAR_UPLOAD_FAILED",
                    "Não foi possível enviar a foto. Tente novamente.");
        }

        return projectUrl + "/storage/v1/object/public/" + encodedBucket + "/" + encodedPath;
    }
}
