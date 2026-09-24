package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.user.enums.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/profiles/{handle}/lists")
@RequiredArgsConstructor
public class HQPublicListController {
    private final HQProfileRepository hqs;
    private final MediaListRepository lists;
    private final MediaListItemRepository items;

    @GetMapping
    @Transactional(readOnly = true)
    public List<MediaListResponse> list(@PathVariable String handle) {
        HQProfile hq = hqs.findByProfileHandleIgnoreCase(handle).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Perfil HQ não encontrado"));
        var rows = lists.findAllByHqProfileIdOrderByUpdatedAtDesc(hq.getId()).stream()
                .filter(item -> item.getVisibility() == Visibility.PUBLIC)
                .toList();
        var counts = rows.isEmpty() ? java.util.Map.<UUID, Long>of() : items.countByListIds(rows.stream().map(MediaList::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(MediaListItemRepository.MediaListItemCount::getListId, MediaListItemRepository.MediaListItemCount::getItemCount));
        return rows.stream().map(item -> MediaListResponse.from(item, counts.getOrDefault(item.getId(), 0L))).toList();
    }
}
