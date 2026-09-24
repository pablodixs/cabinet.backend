package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.common.api.RichTextDocument;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.profile.entity.HQMember;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQMemberRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/hq/{profileId}/lists")
@RequiredArgsConstructor
@Validated
public class HQListController {
    private final HQProfileRepository hqs;
    private final HQMemberRepository members;
    private final MediaListRepository lists;
    private final MediaListItemRepository items;
    private final UserRepository users;


    @GetMapping
    @Transactional(readOnly = true)
    public List<MediaListResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID profileId) {
        HQProfile hq = hq(profileId);
        authorize(actor.id(), hq, false);
        var rows = lists.findAllByHqProfileIdOrderByUpdatedAtDesc(hq.getId());
        var counts = rows.isEmpty() ? java.util.Map.<UUID, Long>of() : items.countByListIds(rows.stream().map(MediaList::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(MediaListItemRepository.MediaListItemCount::getListId, MediaListItemRepository.MediaListItemCount::getItemCount));
        return rows.stream().map(item -> MediaListResponse.from(item, counts.getOrDefault(item.getId(), 0L))).toList();
    }

    @PostMapping
    @Transactional
    public MediaListResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID profileId,
                                    @Valid @RequestBody ListRequest request) {
        HQProfile hq = hq(profileId);
        authorize(actor.id(), hq, true);
        MediaList list = new MediaList();
        list.setOwner(users.findById(actor.id()).orElseThrow());
        list.setHqProfile(hq);
        list.setName(request.name().trim());
        list.setDescription(request.description());
        applyRichDescription(list, request);
        list.setVisibility(request.visibility());
        list.setOrdered(request.ordered());
        list.setCoverUrl(request.coverUrl());
        return MediaListResponse.from(lists.save(list), items.countByListId(list.getId()));
    }

    @PutMapping("/{listId}")
    @Transactional
    public MediaListResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID profileId,
                                    @PathVariable UUID listId,
                                    @Valid @RequestBody ListRequest request) {
        HQProfile hq = hq(profileId);
        authorize(actor.id(), hq, true);
        MediaList list = lists.findByIdAndHqProfileId(listId, hq.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "LIST_NOT_FOUND", "Lista não encontrada"));
        list.setName(request.name().trim());
        list.setDescription(request.description());
        applyRichDescription(list, request);
        list.setVisibility(request.visibility());
        list.setOrdered(request.ordered());
        list.setCoverUrl(request.coverUrl());
        list.setBackdropUrl(request.backdropUrl());
        return MediaListResponse.from(lists.save(list), items.countByListId(list.getId()));
    }

    @DeleteMapping("/{listId}")
    @Transactional
    public void delete(@AuthenticationPrincipal AuthenticatedUser actor,
                       @PathVariable UUID profileId, @PathVariable UUID listId) {
        HQProfile hq = hq(profileId);
        authorize(actor.id(), hq, true);
        MediaList list = lists.findByIdAndHqProfileId(listId, hq.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "LIST_NOT_FOUND", "Lista não encontrada"));
        lists.delete(list);
    }

    private HQProfile hq(UUID profileId) {
        return hqs.findByProfileId(profileId).orElseThrow(
                () -> error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Perfil HQ não encontrado"));
    }

    private void authorize(UUID accountId, HQProfile hq, boolean write) {
        if (hq.getClaimStatus() == com.scriptles.cabinet.profile.enums.HQClaimStatus.SUSPENDED) {
            throw error(HttpStatus.FORBIDDEN, "HQ_SUSPENDED", "HQ suspensa");
        }
        if (users.findById(accountId).map(user -> user.getRole() != null && user.getRole().name().equals("ADMIN")).orElse(false)) return;
        HQMember member = members.findByHqProfileIdAndAccountId(hq.getId(), accountId).orElseThrow(
                () -> error(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Você não pertence à equipe"));
        if (write && member.getRole() != HQMemberRole.OWNER && member.getRole() != HQMemberRole.ADMIN
                && member.getRole() != HQMemberRole.EDITOR) {
            throw error(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Permissão insuficiente");
        }
    }

    private ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }

    private void applyRichDescription(MediaList list, ListRequest request) {
        if (request.richDescription() == null) { list.setRichDescription(null); return; }
        String plain = RichTextDocument.validateAndExtractText(request.richDescription(), false);
        if (plain.length() > 2000) throw new com.scriptles.cabinet.common.api.ApiException(HttpStatus.BAD_REQUEST,
                "LIST_DESCRIPTION_TOO_LONG", "A descrição deve ter no máximo 2000 caracteres");
        if (request.description() != null && !request.description().equals(plain)) {
            throw new com.scriptles.cabinet.common.api.ApiException(HttpStatus.BAD_REQUEST,
                    "RICH_TEXT_MISMATCH", "A descrição deve corresponder ao documento formatado");
        }
        list.setDescription(plain);
        list.setRichDescription(request.richDescription().toString());
    }

    public record ListRequest(@NotBlank @Size(max = 120) String name,
                              @Size(max = 2000) String description,
                              @NotNull Visibility visibility,
                              @NotNull Boolean ordered,
                              @Size(max = 500) String coverUrl,
                              @Size(max = 500) String backdropUrl,
                              tools.jackson.databind.JsonNode richDescription) {}
}
