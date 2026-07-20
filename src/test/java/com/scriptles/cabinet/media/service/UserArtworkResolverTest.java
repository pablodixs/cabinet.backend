package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaArtworkPreference;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.repository.UserMediaArtworkPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserArtworkResolverTest {
    @Test
    void appliesOnlyPreferencesReturnedForTheProEntitlementQuery() {
        UserMediaArtworkPreferenceRepository repository = mock(UserMediaArtworkPreferenceRepository.class);
        UserArtworkResolver resolver = new UserArtworkResolver(repository);
        UUID viewerId = UUID.randomUUID();
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setTitle("Movie");
        media.setCoverUrl("canonical-cover");
        media.setBackdropUrl("canonical-backdrop");
        UserMediaArtworkPreference preference = new UserMediaArtworkPreference();
        preference.setUser(new User());
        preference.setMedia(media);
        preference.setCoverUrl("custom-cover");
        preference.setBackdropUrl("custom-backdrop");
        when(repository.findActiveByUserAndMediaIds(
                eq(viewerId), eq(AccountTier.PRO), argThat(ids -> ids.contains(media.getId()))))
                .thenReturn(List.of(preference));

        var resolved = resolver.resolve(viewerId, List.of(media)).get(media.getId());

        assertThat(resolved.coverUrl()).isEqualTo("custom-cover");
        assertThat(resolved.backdropUrl()).isEqualTo("custom-backdrop");
        assertThat(resolved.customCover()).isTrue();
        verify(repository).findActiveByUserAndMediaIds(
                eq(viewerId), eq(AccountTier.PRO), argThat(ids -> ids.contains(media.getId())));
    }

    @Test
    void anonymousViewsUseCanonicalArtworkWithoutQueryingPreferences() {
        UserMediaArtworkPreferenceRepository repository = mock(UserMediaArtworkPreferenceRepository.class);
        UserArtworkResolver resolver = new UserArtworkResolver(repository);
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setTitle("Movie");
        media.setCoverUrl("canonical-cover");

        var resolved = resolver.resolve(null, List.of(media)).get(media.getId());

        assertThat(resolved.coverUrl()).isEqualTo("canonical-cover");
        assertThat(resolved.customCover()).isFalse();
        verifyNoInteractions(repository);
    }
}
