package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCommunityCacheInvalidatorTest {
    @Mock AlbumTrackRepository albumTrackRepository;
    @Mock SeriesEpisodeRepository seriesEpisodeRepository;

    @Test
    void evictsTrackAndEveryParentAlbum() {
        UUID trackId = UUID.randomUUID();
        UUID firstAlbumId = UUID.randomUUID();
        UUID secondAlbumId = UUID.randomUUID();
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("mediaCommunity");
        var cache = cacheManager.getCache("mediaCommunity");
        cache.put(trackId, "track");
        cache.put(firstAlbumId, "first");
        cache.put(secondAlbumId, "second");
        when(albumTrackRepository.findAlbumIdsByTrackMediaId(trackId))
                .thenReturn(List.of(firstAlbumId, secondAlbumId));
        var invalidator = new MediaCommunityCacheInvalidator(
                cacheManager, albumTrackRepository, seriesEpisodeRepository);

        invalidator.evict(media(trackId, MediaType.TRACK));

        assertThat(cache.get(trackId)).isNull();
        assertThat(cache.get(firstAlbumId)).isNull();
        assertThat(cache.get(secondAlbumId)).isNull();
    }

    @Test
    void evictsEpisodeAndParentSeries() {
        UUID episodeId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("mediaCommunity");
        var cache = cacheManager.getCache("mediaCommunity");
        cache.put(episodeId, "episode");
        cache.put(seriesId, "series");
        when(seriesEpisodeRepository.findSeriesIdByEpisodeMediaId(episodeId))
                .thenReturn(Optional.of(seriesId));
        var invalidator = new MediaCommunityCacheInvalidator(
                cacheManager, albumTrackRepository, seriesEpisodeRepository);

        invalidator.evict(media(episodeId, MediaType.EPISODE));

        assertThat(cache.get(episodeId)).isNull();
        assertThat(cache.get(seriesId)).isNull();
    }

    private Media media(UUID id, MediaType type) {
        Media media = new Media();
        media.setId(id);
        media.setType(type);
        return media;
    }
}
