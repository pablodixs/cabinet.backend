package com.scriptles.cabinet.profile.controller;
import com.scriptles.cabinet.profile.dto.OrganizationResponse;
import com.scriptles.cabinet.profile.repository.MediaOrganizationRepository;
import com.scriptles.cabinet.profile.repository.HQCatalogLinkRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/v1/media/{mediaId}/organizations") @RequiredArgsConstructor
public class MediaOrganizationController {
    private final MediaOrganizationRepository links;
    private final HQCatalogLinkRepository hqLinks;
    private final HQProfileRepository hqs;
    @GetMapping public List<OrganizationResponse> list(@PathVariable UUID mediaId) {
        return links.findByMediaId(mediaId).stream().map(link -> {
            var organization = link.getOrganization();
            var profile = hqLinks.findByOrganizationId(organization.getId()).stream().findFirst().map(l -> l.getHqProfile().getProfile()).orElse(null);
            return new OrganizationResponse(organization.getId(), organization.getCanonicalName(), organization.getType(), link.getRelationship(), organization.getCountryCode(), organization.getLogoUrl(), profile == null ? null : profile.getId(), profile == null ? null : profile.getHandle(), profile != null && profile.isVerified());
        }).toList();
    }
}
