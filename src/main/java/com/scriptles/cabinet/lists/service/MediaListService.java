package com.scriptles.cabinet.lists.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.RichTextDocument;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.dto.request.AddMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.request.CreateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.DuplicateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.UpdateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.ReorderMediaListItemsRequest;
import com.scriptles.cabinet.lists.dto.response.MediaListDetailsResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListBackdropOptionResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListBackdropOptionsResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListPreviewResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListMembershipResponse;
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
import com.scriptles.cabinet.media.service.UserMediaArtworkService;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.SocialAccessPolicy;
import com.scriptles.cabinet.user.service.UserTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final UserMediaArtworkService userMediaArtworkService;
    private final SocialAccessPolicy socialAccessPolicy;
    private final UserTagService userTagService;

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
        Map<UUID, List<MediaListPreviewResponse>> previewItems = previewItems(lists);

        return lists.stream()
                .map(list -> MediaListResponse.from(
                        list,
                        itemCounts.getOrDefault(list.getId(), 0L),
                        previewItems.getOrDefault(list.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MediaListMembershipResponse> findMemberships(UUID userId, UUID mediaId) {
        return mediaListItemRepository.findByMediaIdAndListOwnerId(mediaId, userId)
                .stream()
                .map(item -> new MediaListMembershipResponse(item.getList().getId(), item.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicListSearchResponse> findByOwner(
            String username,
            UUID viewerId,
            int page,
            int size
    ) {
        User owner = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "profile_not_found", "Perfil não encontrado"));
        if (!socialAccessPolicy.canViewProfile(owner, viewerId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "profile_not_found", "Perfil não encontrado");
        }

        Page<MediaList> lists = mediaListRepository.findAccessibleByOwner(
                owner.getId(), viewerId, PageRequest.of(page, size));
        List<UUID> listIds = lists.stream().map(MediaList::getId).toList();
        Map<UUID, Long> itemCounts = listIds.isEmpty()
                ? Map.of()
                : mediaListItemRepository.countByListIds(listIds).stream()
                        .collect(Collectors.toMap(
                                MediaListItemRepository.MediaListItemCount::getListId,
                                MediaListItemRepository.MediaListItemCount::getItemCount));
        Map<UUID, Long> likeCounts = listIds.isEmpty()
                ? Map.of()
                : mediaListLikeRepository.countByListIds(listIds).stream()
                        .collect(Collectors.toMap(
                                MediaListLikeRepository.MediaListLikeCount::getListId,
                                MediaListLikeRepository.MediaListLikeCount::getLikeCount));
        Map<UUID, List<MediaListPreviewResponse>> previews =
                listIds.isEmpty() ? Map.of() : previewItems(lists.getContent());
        Map<UUID, ListConsumption> consumption =
                consumptionByListIds(viewerId, listIds, itemCounts);

        return PageResponse.from(lists.map(list -> {
            ListConsumption progress = consumption.get(list.getId());
            return PublicListSearchResponse.from(
                    list,
                    itemCounts.getOrDefault(list.getId(), 0L),
                    likeCounts.getOrDefault(list.getId(), 0L),
                    previews.getOrDefault(list.getId(), List.of()),
                    progress == null ? null : progress.consumedItemCount(),
                    progress == null ? null : progress.percentage()
            );
        }));
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
        Page<MediaListItemRepository.MediaListPopularity> memberships = viewerId == null
                ? mediaListItemRepository.findAllByMediaIdAndListVisibility(
                        mediaId, Visibility.PUBLIC, PageRequest.of(page, size))
                : mediaListItemRepository.findAllAccessibleByMediaId(
                        mediaId, viewerId, PageRequest.of(page, size));

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
        Map<UUID, List<MediaListPreviewResponse>> previewItems =
                previewItems(listsById.values());
        Map<UUID, ListConsumption> consumption =
                consumptionByListIds(viewerId, listIds, itemCounts);

        return PageResponse.from(memberships.map(membership -> {
            ListConsumption progress = consumption.get(membership.getListId());
            return PublicMediaListResponse.from(
                    listsById.get(membership.getListId()),
                    membership.getItem(),
                    itemCounts.getOrDefault(membership.getListId(), 0L),
                    membership.getLikeCount(),
                    previewItems.getOrDefault(membership.getListId(), List.of()),
                    progress == null ? null : progress.consumedItemCount(),
                    progress == null ? null : progress.percentage()
            );
        }));
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
        Page<MediaList> lists = viewerId == null
                ? mediaListRepository.searchPublicLists(
                        query.trim(), Visibility.PUBLIC, PageRequest.of(page, size))
                : mediaListRepository.searchAccessibleLists(
                        query.trim(), viewerId, PageRequest.of(page, size));
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
        Map<UUID, List<MediaListPreviewResponse>> previewItems =
                previewItems(lists.getContent());
        Map<UUID, ListConsumption> consumption =
                consumptionByListIds(viewerId, listIds, itemCounts);

        return PageResponse.from(lists.map(list -> {
            ListConsumption progress = consumption.get(list.getId());
            return PublicListSearchResponse.from(
                    list,
                    itemCounts.getOrDefault(list.getId(), 0L),
                    likeCounts.getOrDefault(list.getId(), 0L),
                    previewItems.getOrDefault(list.getId(), List.of()),
                    progress == null ? null : progress.consumedItemCount(),
                    progress == null ? null : progress.percentage()
            );
        }));
    }

    @Transactional(readOnly = true)
    public List<PublicListSearchResponse> findGloballyPopular(int limit) {
        return findGloballyPopular(limit, null);
    }

    @Transactional(readOnly = true)
    public List<PublicListSearchResponse> findGloballyPopular(int limit, UUID viewerId) {
        List<MediaListRepository.PopularListProjection> popular = viewerId == null
                ? mediaListRepository.findPopularPublicLists(
                        Visibility.PUBLIC, PageRequest.of(0, limit))
                : mediaListRepository.findPopularAccessibleLists(
                        viewerId, PageRequest.of(0, limit));
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
        Map<UUID, List<MediaListPreviewResponse>> previewItems =
                previewItems(listsById.values());
        Map<UUID, ListConsumption> consumption =
                consumptionByListIds(viewerId, listIds, itemCounts);

        return popular.stream()
                .map(item -> {
                    MediaList list = listsById.get(item.getListId());
                    ListConsumption progress = consumption.get(item.getListId());
                    return list == null ? null : PublicListSearchResponse.from(
                            list,
                            itemCounts.getOrDefault(item.getListId(), 0L),
                            item.getLikeCount(),
                            previewItems.getOrDefault(item.getListId(), List.of()),
                            progress == null ? null : progress.consumedItemCount(),
                            progress == null ? null : progress.percentage()
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

        boolean accessible = socialAccessPolicy == null
                ? list.getVisibility() == Visibility.PUBLIC || ownList
                : socialAccessPolicy.canViewContent(
                        list.getOwner().getId(), userId, list.getVisibility());
        if (!accessible) {
            throw listNotFound();
        }

        List<MediaListItemResponse> items = itemResponses(
                listId, list.getOwner().getId(), userId);
        boolean liked = userId != null
                && mediaListLikeRepository.existsByUserIdAndListId(userId, listId);
        Map<UUID, Long> itemCounts = Map.of(listId, (long) items.size());
        ListConsumption progress = consumptionByListIds(
                userId, List.of(listId), itemCounts).get(listId);

        return PublicMediaListDetailsResponse.from(
                list,
                items,
                mediaListLikeRepository.countByListId(listId),
                liked,
                ownList,
                progress == null ? null : progress.consumedItemCount(),
                progress == null ? null : progress.percentage()
        );
    }

    private Map<UUID, ListConsumption> consumptionByListIds(
            UUID viewerId,
            List<UUID> listIds,
            Map<UUID, Long> itemCounts
    ) {
        if (viewerId == null || listIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> consumedCounts = mediaListItemRepository
                .countConsumedByListIds(
                        viewerId, listIds, UserMediaStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(
                        MediaListItemRepository.MediaListConsumptionCount::getListId,
                        MediaListItemRepository.MediaListConsumptionCount::getConsumedItemCount
                ));

        return listIds.stream().collect(Collectors.toMap(
                Function.identity(),
                listId -> {
                    long total = itemCounts.getOrDefault(listId, 0L);
                    long consumed = consumedCounts.getOrDefault(listId, 0L);
                    int percentage = total == 0
                            ? 0
                            : (int) Math.round(consumed * 100.0 / total);
                    return new ListConsumption(consumed, percentage);
                }
        ));
    }

    private record ListConsumption(long consumedItemCount, int percentage) {
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
        if (request.richDescription() != null) {
            String text = RichTextDocument.validateAndExtractText(request.richDescription(), false);
            if (text.length() > 2000) throw new ApiException(HttpStatus.BAD_REQUEST,"LIST_DESCRIPTION_TOO_LONG","A descrição deve ter no máximo 2000 caracteres");
            if (request.description() != null && !text.equals(request.description())) throw new ApiException(HttpStatus.BAD_REQUEST,"RICH_TEXT_MISMATCH","A descrição deve corresponder ao documento formatado");
            list.setDescription(normalizeOptional(text)); list.setRichDescription(request.richDescription().toString());
        }
        list.setVisibility(request.visibility() == null ? Visibility.PUBLIC : request.visibility());
        list.setOrdered(request.ordered() == null || request.ordered());
        applyCover(owner, list, request.coverUrl());
        list.setTags(new LinkedHashSet<>(
                userTagService.resolveTags(owner, request.tags())));

        return MediaListResponse.from(mediaListRepository.saveAndFlush(list), 0);
    }

    @Transactional
    public MediaListResponse duplicate(
            UUID userId,
            UUID sourceListId,
            DuplicateMediaListRequest request
    ) {
        User owner = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
        requirePro(owner, "A duplicação de listas está disponível para usuários Pro");

        MediaList source = mediaListRepository.findWithOwnerById(sourceListId)
                .orElseThrow(() -> listNotFound());
        boolean ownList = source.getOwner().getId().equals(userId);
        boolean accessible = socialAccessPolicy == null
                ? source.getVisibility() == Visibility.PUBLIC || ownList
                : socialAccessPolicy.canViewContent(
                        source.getOwner().getId(), userId, source.getVisibility());
        if (!accessible) {
            throw listNotFound();
        }

        MediaList duplicate = new MediaList();
        duplicate.setOwner(owner);
        duplicate.setName(request.name().trim());
        duplicate.setDescription(source.getDescription());
        duplicate.setRichDescription(source.getRichDescription());
        duplicate.setVisibility(Visibility.PRIVATE);
        duplicate.setOrdered(source.isOrdered());
        duplicate.setCoverUrl(source.getCoverUrl());
        duplicate.setBackdropUrl(source.getBackdropUrl());
        duplicate.setBackdropMedia(source.getBackdropMedia());
        duplicate.setBackdropKey(source.getBackdropKey());
        MediaList savedList = mediaListRepository.saveAndFlush(duplicate);

        List<MediaListItem> sourceItems =
                mediaListItemRepository.findAllWithMediaByListId(sourceListId);
        List<MediaListItem> copiedItems = sourceItems.stream()
                .map(sourceItem -> {
                    MediaListItem copiedItem = new MediaListItem();
                    copiedItem.setList(savedList);
                    copiedItem.setMedia(sourceItem.getMedia());
                    copiedItem.setPosition(sourceItem.getPosition());
                    return copiedItem;
                })
                .toList();
        mediaListItemRepository.saveAllAndFlush(copiedItems);

        return MediaListResponse.from(savedList, copiedItems.size());
    }

    @Transactional
    public void delete(UUID userId, UUID listId) {
        MediaList list = findOwnedList(userId, listId);
        mediaListLikeRepository.deleteByListId(listId);
        mediaListItemRepository.deleteByListId(listId);
        mediaListRepository.delete(list);
    }

    @Transactional(readOnly = true)
    public MediaListDetailsResponse findDetails(UUID userId, UUID listId) {
        MediaList list = findOwnedList(userId, listId);
        return MediaListDetailsResponse.from(
                list,
                itemResponses(listId, list.getOwner().getId())
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
        if (request.richDescription() != null) {
            String text = RichTextDocument.validateAndExtractText(request.richDescription(), false);
            if (text.length() > 2000) throw new ApiException(HttpStatus.BAD_REQUEST,"LIST_DESCRIPTION_TOO_LONG","A descrição deve ter no máximo 2000 caracteres");
            if (request.description() != null && !text.equals(request.description())) throw new ApiException(HttpStatus.BAD_REQUEST,"RICH_TEXT_MISMATCH","A descrição deve corresponder ao documento formatado");
            list.setDescription(normalizeOptional(text)); list.setRichDescription(request.richDescription().toString());
        } else {
            list.setRichDescription(null);
        }
        list.setVisibility(request.visibility());
        list.setOrdered(request.ordered());
        applyCover(list.getOwner(), list, request.coverUrl());
        list.getTags().clear();
        list.getTags().addAll(userTagService.resolveTags(
                list.getOwner(), request.tags()));
        applyBackdrop(
                list.getOwner(),
                list,
                request.backdropMediaId(),
                normalizeOptional(request.backdropKey())
        );

        return MediaListResponse.from(
                mediaListRepository.saveAndFlush(list),
                mediaListItemRepository.countByListId(listId),
                previewItems(List.of(list)).getOrDefault(listId, List.of())
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
        if (list.getBackdropMedia() != null
                && list.getBackdropMedia().getId().equals(item.getMedia().getId())) {
            clearBackdrop(list);
        }
        mediaListItemRepository.delete(item);
        mediaListItemRepository.decrementPositionsAfter(listId, removedPosition);
        touch(list);
    }

    @Transactional
    public void reorderItems(UUID userId, UUID listId, ReorderMediaListItemsRequest request) {
        MediaList list = findOwnedList(userId, listId);
        if (!list.isOrdered()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIST_NOT_ORDERED",
                    "Esta lista não permite ordenação manual");
        }
        List<MediaListItem> items = mediaListItemRepository.findAllWithMediaByListId(listId);
        Set<UUID> current = items.stream().map(MediaListItem::getId).collect(Collectors.toSet());
        List<UUID> requested = request.itemIds();
        if (requested.size() != current.size()
                || new java.util.HashSet<>(requested).size() != requested.size()
                || !current.equals(new java.util.HashSet<>(requested))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIST_ORDER",
                    "A ordem deve conter todos os itens da lista uma única vez");
        }
        Map<UUID, MediaListItem> byId = items.stream()
                .collect(Collectors.toMap(MediaListItem::getId, item -> item));
        for (int index = 0; index < requested.size(); index++) {
            byId.get(requested.get(index)).setPosition(index + 1);
        }
        mediaListItemRepository.saveAllAndFlush(items);
        touch(list);
    }

    @Transactional(readOnly = true)
    public MediaListBackdropOptionsResponse findBackdropOptions(
            UUID userId,
            UUID listId
    ) {
        MediaList list = findOwnedList(userId, listId);
        requirePro(list.getOwner());
        List<MediaListItem> items = mediaListItemRepository.findAllWithMediaByListId(listId);
        List<MediaListBackdropOptionResponse> options = items.stream()
                .filter(item -> item.getMedia().getType()
                        == com.scriptles.cabinet.media.enums.MediaType.MOVIE
                        || item.getMedia().getType()
                        == com.scriptles.cabinet.media.enums.MediaType.SERIES)
                .flatMap(item -> backdropOptions(
                        list.getOwner().getId(), item.getMedia()).stream())
                .toList();

        if (list.getBackdropMedia() != null
                && list.getBackdropKey() != null
                && list.getBackdropUrl() != null
                && options.stream().noneMatch(option ->
                        option.mediaId().equals(list.getBackdropMedia().getId())
                                && option.key().equals(list.getBackdropKey()))) {
            List<MediaListBackdropOptionResponse> withCurrent =
                    new ArrayList<>(options.size() + 1);
            withCurrent.add(new MediaListBackdropOptionResponse(
                    list.getBackdropMedia().getId(),
                    list.getBackdropMedia().getTitle(),
                    list.getBackdropKey(),
                    list.getBackdropUrl(),
                    list.getBackdropUrl(),
                    null,
                    null
            ));
            withCurrent.addAll(options);
            options = List.copyOf(withCurrent);
        }

        return new MediaListBackdropOptionsResponse(
                list.getId(),
                list.getBackdropMedia() == null ? null : list.getBackdropMedia().getId(),
                list.getBackdropKey(),
                options
        );
    }

    private List<MediaListBackdropOptionResponse> backdropOptions(
            UUID ownerId,
            Media media
    ) {
        try {
            return userMediaArtworkService.findOptions(
                            ownerId, media.getId()
                    ).backdropOptions().stream()
                    .filter(option -> option.language() == null)
                    .map(option -> MediaListBackdropOptionResponse.from(
                            media.getId(), media.getTitle(), option))
                    .toList();
        } catch (ApiException ignored) {
            return List.of();
        }
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

    private Map<UUID, List<MediaListPreviewResponse>> previewItems(
            java.util.Collection<MediaList> lists
    ) {
        if (lists.isEmpty()) {
            return Map.of();
        }

        List<UUID> listIds = lists.stream().map(MediaList::getId).toList();
        Map<UUID, UUID> ownerByListId = lists.stream().collect(Collectors.toMap(
                MediaList::getId,
                list -> list.getOwner().getId(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
        List<MediaListItemRepository.MediaListCover> covers =
                mediaListItemRepository.findRecentCoversByListIds(listIds);
        Map<UUID, Media> mediaById = mediaRepository.findAllById(
                        covers.stream().map(MediaListItemRepository.MediaListCover::getMediaId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        Map<UUID, Map<UUID, UserArtworkResolver.ResolvedArtwork>> artworksByOwner =
                ownerByListId.values().stream()
                        .distinct()
                        .collect(Collectors.toMap(
                                Function.identity(),
                                ownerId -> resolveArtwork(
                                        ownerId,
                                        covers.stream()
                                                .filter(cover -> ownerId.equals(
                                                        ownerByListId.get(cover.getListId())))
                                                .map(cover -> mediaById.get(cover.getMediaId()))
                                                .filter(Objects::nonNull)
                                                .toList()
                                )
                        ));
        Map<UUID, List<MediaListPreviewResponse>> previewsByListId = new LinkedHashMap<>();
        covers.forEach(cover -> {
            Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = artworksByOwner
                    .getOrDefault(ownerByListId.get(cover.getListId()), Map.of());
            previewsByListId
                    .computeIfAbsent(cover.getListId(), ignored -> new ArrayList<>())
                    .add(new MediaListPreviewResponse(
                            artworks.containsKey(cover.getMediaId())
                                    ? artworks.get(cover.getMediaId()).coverUrl()
                                    : cover.getCoverUrl(),
                            cover.getType()));
        });
        return previewsByListId;
    }

    private List<MediaListItemResponse> itemResponses(UUID listId, UUID artworkOwnerId) {
        return itemResponses(listId, artworkOwnerId, null);
    }

    private List<MediaListItemResponse> itemResponses(
            UUID listId,
            UUID artworkOwnerId,
            UUID viewerId
    ) {
        List<MediaListItem> items = mediaListItemRepository.findAllWithMediaByListId(listId);
        Map<UUID, ExternalReference> referencesByMediaId = primaryReferences(items);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                artworkOwnerId, items.stream().map(MediaListItem::getMedia).toList());
        Set<UUID> consumedMediaIds = viewerId == null
                ? Set.of()
                : Set.copyOf(mediaListItemRepository.findConsumedMediaIds(
                        viewerId, listId, UserMediaStatus.COMPLETED));

        return items.stream()
                .map(item -> MediaListItemResponse.from(
                        item,
                        referencesByMediaId.get(item.getMedia().getId()),
                        artworks.get(item.getMedia().getId()).coverUrl(),
                        consumedMediaIds.contains(item.getMedia().getId())
                ))
                .toList();
    }

    private MediaList findOwnedList(UUID userId, UUID listId) {
        MediaList list = mediaListRepository.findWithOwnerById(listId)
                .orElseThrow(this::listNotFound);
        if (list.getHqProfile() == null && list.getOwner().getId().equals(userId)) return list;
        throw listNotFound();
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

    private void applyCover(User owner, MediaList list, String requestedCoverUrl) {
        String coverUrl = normalizeOptional(requestedCoverUrl);
        if (coverUrl != null) {
            requirePro(
                    owner,
                    "A capa personalizada de listas está disponível para usuários Pro"
            );
        }
        list.setCoverUrl(coverUrl);
    }

    private void applyBackdrop(
            User owner,
            MediaList list,
            UUID backdropMediaId,
            String backdropKey
    ) {
        if (backdropMediaId == null && backdropKey == null) {
            clearBackdrop(list);
            return;
        }
        if (backdropMediaId == null || backdropKey == null) {
            throw invalidBackdrop();
        }
        requirePro(owner);
        if (!mediaListItemRepository.existsByListIdAndMediaId(
                list.getId(), backdropMediaId)) {
            throw invalidBackdrop();
        }
        if (list.getBackdropMedia() != null
                && list.getBackdropMedia().getId().equals(backdropMediaId)
                && backdropKey.equals(list.getBackdropKey())
                && list.getBackdropUrl() != null) {
            return;
        }
        Media media = findMedia(backdropMediaId);
        var selected = userMediaArtworkService.findOptions(
                        owner.getId(), backdropMediaId)
                .backdropOptions().stream()
                .filter(option -> option.language() == null)
                .filter(option -> option.key().equals(backdropKey))
                .findFirst()
                .orElseThrow(this::invalidBackdrop);
        list.setBackdropMedia(media);
        list.setBackdropKey(selected.key());
        list.setBackdropUrl(selected.url());
    }

    private void clearBackdrop(MediaList list) {
        list.setBackdropMedia(null);
        list.setBackdropKey(null);
        list.setBackdropUrl(null);
    }

    private void requirePro(User owner) {
        requirePro(
                owner,
                "O backdrop personalizável de listas está disponível para usuários Pro"
        );
    }

    private void requirePro(User owner, String message) {
        if (!Boolean.TRUE.equals(owner.getActive())
                || owner.getAccountTier() != AccountTier.PRO) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PRO_REQUIRED",
                    message
            );
        }
    }

    private ApiException invalidBackdrop() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_LIST_BACKDROP",
                "Escolha um backdrop sem idioma de uma mídia contida na lista"
        );
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
