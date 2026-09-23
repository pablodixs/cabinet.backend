package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.response.AvatarUploadResponse;
import com.scriptles.cabinet.user.service.SupabaseAvatarStorage;
import com.scriptles.cabinet.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Set;

@RestController
@RequestMapping("/v1/me/profile/avatar")
@RequiredArgsConstructor
public class MeProfileAvatarController {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final SupabaseAvatarStorage storage;
    private final UserService userService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarUploadResponse upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE || file.getContentType() == null
                || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_FILE",
                    "Envie uma imagem JPEG ou PNG de até 5 MB");
        }
        try {
            byte[] image = file.getBytes();
            String imageFormat = imageFormat(image);
            String expectedFormat = "image/png".equals(file.getContentType()) ? "png" : "jpeg";
            if (!expectedFormat.equals(imageFormat)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_FILE",
                        "O arquivo enviado não é uma imagem válida");
            }
            String avatarUrl = storage.upload(user.id(), image, file.getContentType());
            userService.updateAvatar(user.id(), avatarUrl);
            return new AvatarUploadResponse(avatarUrl);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_FILE",
                    "Não foi possível ler a imagem enviada");
        }
    }

    private String imageFormat(byte[] image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > 4096 || height > 4096 || (long) width * height > 16_000_000L) return null;
                return reader.getFormatName().equalsIgnoreCase("png") ? "png"
                        : reader.getFormatName().equalsIgnoreCase("jpeg") || reader.getFormatName().equalsIgnoreCase("jpg")
                        ? "jpeg" : null;
            } finally {
                reader.dispose();
            }
        }
    }
}
