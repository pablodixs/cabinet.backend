package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaCommunityCacheInvalidator {
    private final CacheManager cacheManager;
    private final AlbumTrackRepository albumTrackRepository;
    private final SeriesEpisodeRepository seriesEpisodeRepository;

    public void evict(Media media) {
        Cache cache = cacheManager.getCache("mediaCommunity");
        if (cache == null) return;

        cache.evict(media.getId());
        switch (media.getType()) {
            case TRACK -> albumTrackRepository.findAlbumIdsByTrackMediaId(media.getId())
                    .forEach(cache::evict);
            case EPISODE -> seriesEpisodeRepository.findSeriesIdByEpisodeMediaId(media.getId())
                    .ifPresent(cache::evict);
            default -> {
            }
        }
    }
}
