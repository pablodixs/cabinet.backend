package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.profile.dto.HQUpdateRequest;
import com.scriptles.cabinet.profile.dto.ProfileResponse;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.service.HQOperatorService;
import com.scriptles.cabinet.profile.service.ProfileService;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/hq-console") @RequiredArgsConstructor
public class HQConsoleController {
    private final HQOperatorService operators;
    private final ProfileService profiles;
    private final com.scriptles.cabinet.profile.service.PostService posts;
    private final com.scriptles.cabinet.user.service.SupabaseAvatarStorage storage;
    private final SecurityContextRepository contexts;

    public record Login(String handle, String email, String password) {}

    @PostMapping("/login")
    public AuthenticatedHQ login(@RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        if (input == null || input.handle() == null || input.email() == null || input.password() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOGIN", "Informe HQ, e-mail e senha");
        AuthenticatedHQ hq = operators.authenticate(input.handle().trim(), input.email().trim(), input.password());
        var oldSession = request.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(hq, null, hq.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return hq;
    }

    @GetMapping("/me") public AuthenticatedHQ me(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor) {
        return operators.refresh(actor.operatorId());
    }

    @PostMapping("/logout") public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    public record PasswordChange(String currentPassword, String newPassword) {}
    @PutMapping("/password") public void password(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor,
                                                 @RequestBody PasswordChange input) {
        operators.refresh(actor.operatorId());
        if (input == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_CHANGE", "Informe as senhas");
        operators.changePassword(actor.operatorId(), input.currentPassword(), input.newPassword());
    }

    @GetMapping("/profile") public ProfileResponse profile(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        return profiles.findById(current.profileId());
    }

    @PutMapping("/profile") public ProfileResponse update(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor,
                                                           @Valid @RequestBody HQUpdateRequest input) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        requireRole(current, HQMemberRole.OWNER, HQMemberRole.ADMIN);
        return profiles.updateHQByOperator(current.profileId(), input);
    }

    @PostMapping("/posts") public com.scriptles.cabinet.profile.dto.PostResponse post(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor,
            @Valid @RequestBody com.scriptles.cabinet.profile.dto.PostRequest input) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        requireRole(current, HQMemberRole.OWNER, HQMemberRole.ADMIN, HQMemberRole.EDITOR);
        return posts.createByOperator(current.profileId(), input);
    }

    @PostMapping(value = "/backdrop", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse backdrop(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor,
                                    @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        requireRole(current, HQMemberRole.OWNER, HQMemberRole.ADMIN);
        String type = file.getContentType();
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024 || type == null ||
                !(type.equals("image/jpeg") || type.equals("image/png"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKDROP_FILE", "Envie uma imagem JPEG ou PNG de até 5 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() > 8192 || image.getHeight() > 8192 ||
                    (long) image.getWidth() * image.getHeight() > 32_000_000L) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKDROP_FILE", "A imagem enviada não é válida ou é grande demais");
            }
            String url = storage.upload(current.profileId(), bytes, type);
            return profiles.updateHQByOperator(current.profileId(), new HQUpdateRequest(null, null, null, url, null, null, null, null));
        } catch (java.io.IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKDROP_FILE", "Não foi possível ler a imagem enviada");
        }
    }

    @GetMapping("/operators") public List<HQOperatorService.OperatorView> list(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        requireRole(current, HQMemberRole.OWNER, HQMemberRole.ADMIN);
        return operators.list(current.hqId());
    }

    @PostMapping("/operators") public HQOperatorService.OperatorView add(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor,
                                                                         @RequestBody HQOperatorService.OperatorInput input) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        requireRole(current, HQMemberRole.OWNER);
        return operators.create(current.profileId(), input);
    }

    @DeleteMapping("/operators/{id}") public void remove(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID id) {
        AuthenticatedHQ current = operators.refresh(actor.operatorId());
        requireRole(current, HQMemberRole.OWNER);
        operators.deactivate(current.hqId(), id);
    }

    private void requireRole(AuthenticatedHQ actor, HQMemberRole... roles) {
        if (java.util.Arrays.stream(roles).noneMatch(role -> role == actor.role())) throw new ApiException(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Permissão insuficiente");
    }
}
