package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.lists.dto.response.PublicMediaListDetailsResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/lists")
@RequiredArgsConstructor
public class PublicListController {
    private final MediaListService mediaListService;

    @GetMapping("/{listId}")
    public PublicMediaListDetailsResponse findDetails(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId
    ) {
        return mediaListService.findAccessibleDetails(
                user == null ? null : user.id(),
                listId
        );
    }
}
