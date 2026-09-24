package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.entity.HQReview;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.profile.repository.HQReviewRepository;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HQReviewServiceTest {
    @Mock HQReviewRepository reviews;
    @Mock HQProfileRepository hqs;
    @Mock MediaRepository media;
    @InjectMocks HQReviewService service;

    @Test void reviewBelongsToHQAndDoesNotRequirePersonalUser() {
        UUID hqId = UUID.randomUUID(); UUID mediaId = UUID.randomUUID();
        HQProfile hq = new HQProfile(); hq.setId(hqId); hq.setClaimStatus(HQClaimStatus.CLAIMED);
        Media work = new Media(); work.setId(mediaId); work.setTitle("Film");
        when(hqs.findById(hqId)).thenReturn(Optional.of(hq));
        when(media.findById(mediaId)).thenReturn(Optional.of(work));
        when(reviews.findByHqProfileIdAndMediaId(hqId, mediaId)).thenReturn(Optional.empty());
        when(reviews.save(any(HQReview.class))).thenAnswer(invocation -> {
            HQReview review = invocation.getArgument(0); review.setId(UUID.randomUUID()); return review;
        });
        var actor = new AuthenticatedHQ(UUID.randomUUID(), hqId, UUID.randomUUID(), "editor@example.com", "Editor", HQMemberRole.EDITOR, true);
        var result = service.upsert(actor, mediaId, new HQReviewService.ReviewInput("Ótimo filme", false, new BigDecimal("4.5")));
        assertThat(result.mediaId()).isEqualTo(mediaId);
        assertThat(result.rating()).isEqualByComparingTo("4.5");
    }

    @Test void rejectsInvalidRatingBeforeWriting() {
        UUID hqId = UUID.randomUUID();
        HQProfile hq = new HQProfile(); hq.setId(hqId); hq.setClaimStatus(HQClaimStatus.CLAIMED);
        when(hqs.findById(hqId)).thenReturn(Optional.of(hq));
        var actor = new AuthenticatedHQ(UUID.randomUUID(), hqId, UUID.randomUUID(), "owner@example.com", "Owner", HQMemberRole.OWNER, true);
        assertThatThrownBy(() -> service.upsert(actor, UUID.randomUUID(), new HQReviewService.ReviewInput("Teste", false, new BigDecimal("4.3")))).isInstanceOf(ApiException.class);
    }
}
