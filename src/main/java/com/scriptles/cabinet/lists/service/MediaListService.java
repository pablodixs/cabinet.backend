package com.scriptles.cabinet.lists.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.dto.request.AddMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.request.CreateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.UpdateMediaListRequest;
import com.scriptles.cabinet.lists.dto.response.MediaListDetailsResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListPreviewResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListDetailsResponse;
import com.scriptles.cabinet.lists.dto.response.PublicListSearchResponse;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaListService {
    private static final int POPULAR_LIST_LIMIT = 3;

    private final MediaListRepository mediaListRepository;
    private final MediaListItemRepository mediaListItemRepository;
    private final MediaListLikeRepository mediaListLikeRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final UserArtworkResolver userArtworkResolver;

    @Transactional(readOnly = true)
    public List<MediaListResponse> findMine(UUID userId) {
        List<MediaList> lists = mediaListRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId);
        if (lists.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> itemCounts = mediaListItemRepository.countByListIds(
                        lists.stream().map(MediaList::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        MediaListItemRepository.MediaListItemCount::getListId,
                        MediaListItemRepository.MediaListItemCount::getItemCount
                ));
        Map<UUID, List<MediaListPreviewResponse>> previewItems = previewItems(
                lists.stream().map(MediaList::getId).toList(), userId
        );

        return lists.stream()
                .map(list -> MediaListResponse.from(
                        list,
                        itemCounts.getOrDefault(list.getId(), 0L),
                        previewItems.getOrDefault(list.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicMediaListResponse> findPublicByMedia(
            UUID mediaId,
            int page,
            int size
    ) {
        return findPublicByMedia(mediaId, page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicMediaListResponse> findPublicByMedia(
            UUID mediaId,
            int page,
            int size,
            UUID viewerId
    ) {
        Page<MediaListItemRepository.MediaListPopularity> memberships = mediaListItemRepository
                .findAllByMediaIdAndListVisibility(
                        mediaId,
                        Visibility.PUBLIC,
                        PageRequest.of(page, size)
                );

        if (memberships.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    memberships.getNumber(),
                    memberships.getSize(),
                    memberships.getTotalElements(),
                    memberships.getTotalPages()
            );
        }

        List<UUID> listIds = memberships.stream()
                .map(MediaListItemRepository.MediaListPopularity::getListId)
                .toList();
        Map<UUID, Long> itemCounts = mediaListItemRepository.countByListIds(listIds)
                .stream()
                .collect(Collectors.toMap(
                        MediaListItemRepository.MediaListItemCount::getListId,
                        MediaListItemRepository.MediaListItemCount::getItemCount
                ));
        Map<UUID, MediaList> listsById = mediaListRepository
                .findAllWithOwnerByIdIn(listIds)
                .stream()
                .collect(Collectors.toMap(MediaList::getId, Function.identity()));
        Map<UUID, List<MediaListPreviewResponse>> previewItems = previewItems(listIds, viewerId);

        return PageResponse.from(memberships.map(membership -> PublicMediaListResponse.from(
                listsById.get(membership.getListId()),
                membership.getItem(),
                itemCounts.getOrDefault(membership.getListId(), 0L),
                membership.getLikeCount(),
                previewItems.getOrDefault(membership.getListId(), List.of())
        )));
    }

    @Transactional(readOnly = true)
    public List<PublicMediaListResponse> findPopularByMedia(UUID mediaId) {
        return findPublicByMedia(mediaId, 0, POPULAR_LIST_LIMIT).items();
    }

    @Transactional(readOnly = true)
    public List<PublicMediaListResponse> findPopularByMedia(UUID mediaId, UUID viewerId) {
        return findPublicByMedia(mediaId, 0, POPULAR_LIST_LIMIT, viewerId).items();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicListSearchResponse> searchPublic(
            String query,
            int page,
            int size
    ) {
        return searchPublic(query, page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicListSearchResponse> searchPublic(
            String query,
            int page,
            int size,
            UUID viewerId
    ) {
        Page<MediaList> lists = mediaListRepository.searchPublicLists(
                query.trim(),
                Visibility.PUBLIC,
                PageRequest.of(page, size)
        );
        if (lists.isEmpty()) {
            return PageResponse.from(lists.map(list -> PublicListSearchResponse.from(
                    list,
                    0,
                    0,
                    List.of()
            )));
        }

        List<UUID> listIds = lists.stream().map(MediaList::getId).toList();
        Map<UUID, Long> itemCounts = mediaListItemRepository.countByListIds(listIds)
                .stream()
                .collect(Collectors.toMap(
                        MediaListItemRepository.MediaListItemCount::getListId,
                        MediaListItemRepository.MediaListItemCount::getItemCount
                ));
        Map<UUID, Long> likeCounts = mediaListLikeRepository.countByListIds(listIds)
                .stream()
                .collect(Collectors.toMap(
                        MediaListLikeRepository.MediaListLikeCount::getListId,
                        MediaListLikeRepository.MediaListLikeCount::getLikeCount
                ));
        Map<UUID, List<MediaListPreviewResponse>> previewItems = previewItems(listIds, viewerId);

        return PageResponse.from(lists.map(list -> PublicListSearchResponse.from(
                list,
                itemCounts.getOrDefault(list.getId(), 0L),
                likeCounts.getOrDefault(list.getId(), 0L),
                previewItems.getOrDefault(list.getId(), List.of())
        )));
    }

    @Transactional(readOnly = true)
    public List<PublicListSearchResponse> findGloballyPopular(int limit) {
        return findGloballyPopular(limit, null);
    }

    @Transactional(readOnly = true)
    public List<PublicListSearchResponse> findGloballyPopular(int limit, UUID viewerId) {
        List<MediaListRepository.PopularListProjection> popular = mediaListRepository
                .findPopularPublicLists(Visibility.PUBLIC, PageRequest.of(0, limit));
        if (popular.isEmpty()) {
            return List.of();
        }

        List<UUID> listIds = popular.stream()
                .map(MediaListRepository.PopularListProjection::getListId)
                .toList();
        Map<UUID, MediaList> listsById = mediaListRepository.findAllWithOwnerByIdIn(listIds)
                .stream()
                .collect(Collectors.toMap(MediaList::getId, Function.identity()));
        Map<UUID, Long> itemCounts = mediaListItemRepository.countByListIds(listIds)
                .stream()
                .collect(Collectors.toMap(
                        MediaListItemRepository.MediaListItemCount::getListId,
                        MediaListItemRepository.MediaListItemCount::getItemCount
                ));
        Map<UUID, List<MediaListPreviewResponse>> previewItems = previewItems(listIds, viewerId);

        return popular.stream()
                .map(item -> {
                    MediaList list = listsById.get(item.getListId());
                    return list == null ? null : PublicListSearchResponse.from(
                            list,
                            itemCounts.getOrDefault(item.getListId(), 0L),
                            item.getLikeCount(),
                            previewItems.getOrDefault(item.getListId(), List.of())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicMediaListDetailsResponse findAccessibleDetails(
            UUID userId,
            UUID listId
    ) {
        MediaList list = mediaListRepository.findWithOwnerById(listId)
                .orElseThrow(() -> listNotFound());
        boolean ownList = userId != null && list.getOwner().getId().equals(userId);

        if (list.getVisibility() != Visibility.PUBLIC && !ownList) {
            throw listNotFound();
        }

        List<MediaListItemResponse> items = itemResponses(listId, userId);
        boolean liked = userId != null
                && mediaListLikeRepository.existsByUserIdAndListId(userId, listId);

        return PublicMediaListDetailsResponse.from(
                list,
                items,
                mediaListLikeRepository.countByListId(listId),
                liked,
                ownList
        );
    }

    @Transactional
    public MediaListResponse create(UUID userId, CreateMediaListRequest request) {
        User owner = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));

        MediaList list = new MediaList();
        list.setOwner(owner);
        list.setName(request.name().trim());
        list.setDescription(normalizeOptional(request.description()));
        list.setVisibility(request.visibility() == null ? Visibility.PUBLIC : request.visibility());
        list.setOrdered(request.ordered() == null || request.ordered());
        list.setCoverUrl(normalizeOptional(request.coverUrl()));

        return MediaListResponse.from(mediaListRepository.saveAndFlush(list), 0);
    }

    @Transactional(readOnly = true)
    public MediaListDetailsResponse findDetails(UUID userId, UUID listId) {
        MediaList list = findOwnedList(userId, listId);
        return MediaListDetailsResponse.from(
                list,
                itemResponses(listId, userId)
        );
    }

    @Transactional
    public MediaListResponse update(
            UUID userId,
            UUID listId,
            UpdateMediaListRequest request
    ) {
        MediaList list = findOwnedList(userId, listId);
        list.setName(request.name().trim());
        list.setDescription(normalizeOptional(request.description()));
        list.setVisibility(request.visibility());
        list.setOrdered(request.ordered());
        list.setCoverUrl(normalizeOptional(request.coverUrl()));

        return MediaListResponse.from(
                mediaListRepository.saveAndFlush(list),
                mediaListItemRepository.countByListId(listId),
                previewItems(List.of(listId), userId).getOrDefault(listId, List.of())
        );
    }

    @Transactional
    public MediaListItemResponse addItem(
            UUID userId,
            UUID listId,
            AddMediaListItemRequest request
    ) {
        MediaList list = findOwnedList(userId, listId);
        Media media = findMedia(request.mediaId());

        if (mediaListItemRepository.existsByListIdAndMediaId(listId, request.mediaId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "MEDIA_ALREADY_IN_LIST",
                    "Esta mídia já está na lista"
            );
        }

        MediaListItem item = new MediaListItem();
        item.setList(list);
        item.setMedia(media);
        item.setPosition(mediaListItemRepository.findMaxPositionByListId(listId) + 1);
        item.setNotes(normalizeOptional(request.notes()));
        MediaListItem saved = mediaListItemRepository.saveAndFlush(item);
        touch(list);

        ExternalReference reference = externalReferenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(List.of(media.getId()))
                .stream()
                .findFirst()
                .orElse(null);
        return MediaListItemResponse.from(
                saved,
                reference,
                resolveArtwork(userId, List.of(media)).get(media.getId()).coverUrl()
        );
    }

    @Transactional
    public void removeItem(UUID userId, UUID listId, UUID itemId) {
        MediaList list = findOwnedList(userId, listId);
        MediaListItem item = mediaListItemRepository.findByIdAndListId(itemId, listId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "LIST_ITEM_NOT_FOUND",
                        "Item da lista não encontrado"
                ));

        int removedPosition = item.getPosition();
        mediaListItemRepository.delete(item);
        mediaListItemRepository.decrementPositionsAfter(listId, removedPosition);
        touch(list);
    }

    private Map<UUID, ExternalReference> primaryReferences(List<MediaListItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }

        return externalReferenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(
                        items.stream().map(item -> item.getMedia().getId()).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        reference -> reference.getMedia().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private Map<UUID, List<MediaListPreviewResponse>> previewItems(List<UUID> listIds, UUID viewerId) {
        if (listIds.isEmpty()) {
            return Map.of();
        }

        List<MediaListItemRepository.MediaListCover> covers = mediaListItemRepository.findRecentCoversByListIds(listIds);
        Map<UUID, Media> mediaById = mediaRepository.findAllById(
                        covers.stream().map(MediaListItemRepository.MediaListCover::getMediaId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                viewerId, mediaById.values());
        Map<UUID, List<MediaListPreviewResponse>> previewsByListId = new LinkedHashMap<>();
        covers.forEach(cover ->
                previewsByListId
                        .computeIfAbsent(cover.getListId(), ignored -> new ArrayList<>())
                        .add(new MediaListPreviewResponse(
                                artworks.containsKey(cover.getMediaId())
                                        ? artworks.get(cover.getMediaId()).coverUrl()
                                        : cover.getCoverUrl(),
                                cover.getType()))
        );
        return previewsByListId;
    }

    private List<MediaListItemResponse> itemResponses(UUID listId, UUID viewerId) {
        List<MediaListItem> items = mediaListItemRepository.findAllWithMediaByListId(listId);
        Map<UUID, ExternalReference> referencesByMediaId = primaryReferences(items);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                viewerId, items.stream().map(MediaListItem::getMedia).toList());

        return items.stream()
                .map(item -> MediaListItemResponse.from(
                        item,
                        referencesByMediaId.get(item.getMedia().getId()),
                        artworks.get(item.getMedia().getId()).coverUrl()
                ))
                .toList();
    }

    private MediaList findOwnedList(UUID userId, UUID listId) {
        return mediaListRepository.findByIdAndOwnerId(listId, userId)
                .orElseThrow(() -> listNotFound());
    }

    private Map<UUID, UserArtworkResolver.ResolvedArtwork> resolveArtwork(
            UUID viewerId,
            java.util.Collection<Media> mediaItems
    ) {
        if (userArtworkResolver != null) return userArtworkResolver.resolve(viewerId, mediaItems);
        return mediaItems.stream().collect(Collectors.toMap(
                Media::getId,
                media -> new UserArtworkResolver.ResolvedArtwork(
                        media.getCoverUrl(), media.getBackdropUrl(), false, false)
        ));
    }

    private ApiException listNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "LIST_NOT_FOUND",
                "Lista não encontrada"
        );
    }

    private Media findMedia(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
        if (media.getType() == com.scriptles.cabinet.media.enums.MediaType.EPISODE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_CAPABILITY",
                    "Episódios não podem ser adicionados a listas");
        }
        return media;
    }

    private void touch(MediaList list) {
        list.setUpdatedAt(java.time.Instant.now());
        mediaListRepository.save(list);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
