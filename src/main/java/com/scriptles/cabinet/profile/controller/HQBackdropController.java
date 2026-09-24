package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.profile.dto.ProfileResponse;
import com.scriptles.cabinet.profile.service.ProfileService;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.service.SupabaseAvatarStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/v1/hq/{profileId}/backdrop")
@RequiredArgsConstructor
public class HQBackdropController {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private final ProfileService profiles;
    private final SupabaseAvatarStorage storage;
    private final MediaRepository media;

    public record MediaBackdropRequest(UUID mediaId) {}

    @PutMapping("/media")
    public ProfileResponse useMedia(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID profileId,
                                    @RequestBody MediaBackdropRequest request) {
        profiles.ensureCanManageHQ(profileId, actor.id());
        if (request == null || request.mediaId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "Selecione uma mídia");
        }
        var selected = media.findById(request.mediaId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
        if (selected.getBackdropUrl() == null || selected.getBackdropUrl().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BACKDROP_UNAVAILABLE", "Esta mídia não possui backdrop");
        }
        return profiles.updateHQBackdrop(profileId, selected.getBackdropUrl(), actor.id());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse upload(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable UUID profileId,
                                  @RequestPart("file") MultipartFile file) {
        profiles.ensureCanManageHQ(profileId, actor.id());
        String type = file.getContentType();
        if (file.isEmpty() || file.getSize() > MAX_SIZE || type == null
                || !(type.equals("image/jpeg") || type.equals("image/png"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKDROP_FILE", "Envie uma imagem JPEG ou PNG de até 5 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() > 8192 || image.getHeight() > 8192
                    || (long) image.getWidth() * image.getHeight() > 32_000_000L) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKDROP_FILE", "A imagem enviada não é válida ou é grande demais");
            }
            String url = storage.upload(profileId, bytes, type);
            return profiles.updateHQBackdrop(profileId, url, actor.id());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKDROP_FILE", "Não foi possível ler a imagem enviada");
        }
    }
}
