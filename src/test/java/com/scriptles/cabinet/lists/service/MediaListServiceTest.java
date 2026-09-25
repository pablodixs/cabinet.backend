package com.scriptles.cabinet.lists.service;

import com.scriptles.cabinet.common.outbox.DomainOutboxPublisher;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.dto.request.AddMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.request.CreateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.DuplicateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.UpdateMediaListRequest;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionResponse;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionsResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListResponse;
import com.scriptles.cabinet.lists.dto.response.PublicListSearchResponse;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ArtworkProvider;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.service.UserMediaArtworkService;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserTag;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MediaListServiceTest {
    @Mock
    private MediaListRepository mediaListRepository;
    @Mock
    private MediaListItemRepository mediaListItemRepository;
    @Mock
    private MediaListLikeRepository mediaListLikeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ExternalReferenceRepository externalReferenceRepository;
    @Mock
    private UserArtworkResolver userArtworkResolver;
    @Mock
    private UserMediaArtworkService userMediaArtworkService;
    @Mock
    private UserTagService userTagService;
    @Mock
    private DomainOutboxPublisher domainOutboxPublisher;

    @InjectMocks
    private MediaListService mediaListService;

    @BeforeEach
    void resolveCanonicalArtworkByDefault() {
        lenient().when(userArtworkResolver.resolve(
                any(UUID.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<Media>>any()
        )).thenAnswer(invocation -> {
            java.util.Collection<Media> mediaItems = invocation.getArgument(1);
            return mediaItems.stream().collect(java.util.stream.Collectors.toMap(
                    Media::getId,
                    media -> new UserArtworkResolver.ResolvedArtwork(
                            media.getCoverUrl(),
                            media.getBackdropUrl(),
                            false,
                            false
                    )
            ));
        });
    }

    @Test
    void createsListForAuthenticatedOwnerWithSafeDefaults() {
        UUID userId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        UserTag tag = new UserTag();
        tag.setName("para reler");
        tag.setNormalizedName("para reler");
        tag.setUser(owner);
        when(userTagService.resolveTags(owner, Set.of("para reler")))
                .thenReturn(List.of(tag));
        when(mediaListRepository.saveAndFlush(any(MediaList.class)))
                .thenAnswer(invocation -> {
                    MediaList list = invocation.getArgument(0);
                    list.setId(UUID.randomUUID());
                    return list;
                });

        MediaListResponse response = mediaListService.create(
                userId,
                new CreateMediaListRequest(
                        "  Ficções favoritas  ",
                        "  Para reler com calma.  ",
                        null,
                        null,
                        "  ",
                        Set.of("para reler")
                )
        );

        ArgumentCaptor<MediaList> captor = ArgumentCaptor.forClass(MediaList.class);
        verify(mediaListRepository).saveAndFlush(captor.capture());
        MediaList saved = captor.getValue();
        assertThat(saved.getOwner()).isSameAs(owner);
        assertThat(saved.getName()).isEqualTo("Ficções favoritas");
        assertThat(saved.getDescription()).isEqualTo("Para reler com calma.");
        assertThat(saved.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(saved.isOrdered()).isTrue();
        assertThat(saved.getCoverUrl()).isNull();
        assertThat(saved.getTags()).containsExactly(tag);
        assertThat(response.id()).isNotNull();
        assertThat(response.itemCount()).isZero();
        assertThat(response.tags()).containsExactly("para reler");
    }

    @Test
    void duplicatesAccessibleListForProUserAsPrivateCopy() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setActive(true);
        user.setAccountTier(AccountTier.PRO);

        User sourceOwner = new User();
        sourceOwner.setId(UUID.randomUUID());
        MediaList source = mediaList("Cinema de estrada");
        source.setOwner(sourceOwner);
        source.setDescription("Filmes para atravessar o país.");
        source.setCoverUrl("https://images.example/list.jpg");
        UserTag sourceTag = new UserTag();
        sourceTag.setName("viagem");
        source.getTags().add(sourceTag);
        Media media = new Media();
        media.setId(UUID.randomUUID());
        MediaListItem sourceItem = new MediaListItem();
        sourceItem.setList(source);
        sourceItem.setMedia(media);
        sourceItem.setPosition(1);
        sourceItem.setNotes("Começar por este.");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaListRepository.findWithOwnerById(source.getId()))
                .thenReturn(Optional.of(source));
        when(mediaListRepository.saveAndFlush(any(MediaList.class)))
                .thenAnswer(invocation -> {
                    MediaList list = invocation.getArgument(0);
                    list.setId(UUID.randomUUID());
                    return list;
                });
        when(mediaListItemRepository.findAllWithMediaByListId(source.getId()))
                .thenReturn(List.of(sourceItem));
        when(mediaListItemRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<MediaListItem> items = invocation.getArgument(0);
                    assertThat(items).hasSize(1);
                    assertThat(items.getFirst().getMedia()).isSameAs(media);
                    assertThat(items.getFirst().getPosition()).isEqualTo(1);
                    assertThat(items.getFirst().getNotes()).isNull();
                    return items;
                });

        MediaListResponse response = mediaListService.duplicate(
                userId,
                source.getId(),
                new DuplicateMediaListRequest("Minha viagem de cinema")
        );

        ArgumentCaptor<MediaList> listCaptor =
                ArgumentCaptor.forClass(MediaList.class);
        verify(mediaListRepository).saveAndFlush(listCaptor.capture());
        MediaList duplicate = listCaptor.getValue();
        assertThat(duplicate.getOwner()).isSameAs(user);
        assertThat(duplicate.getName()).isEqualTo("Minha viagem de cinema");
        assertThat(duplicate.getDescription())
                .isEqualTo("Filmes para atravessar o país.");
        assertThat(duplicate.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(duplicate.getCoverUrl())
                .isEqualTo("https://images.example/list.jpg");
        assertThat(duplicate.getTags()).isEmpty();
        assertThat(response.itemCount()).isEqualTo(1);
        verify(userTagService, never()).resolveTags(any(), any());
    }

    @Test
    void rejectsCustomListCoverForFreeOwner() {
        UUID userId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        owner.setActive(true);
        owner.setAccountTier(AccountTier.FREE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> mediaListService.create(
                userId,
                new CreateMediaListRequest(
                        "Cinema",
                        null,
                        Visibility.PUBLIC,
                        true,
                        "https://images.example/custom-list.jpg"
                )
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exception.getCode()).isEqualTo("PRO_REQUIRED");
        });

        verify(mediaListRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsListDuplicationForFreeUser() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setActive(true);
        user.setAccountTier(AccountTier.FREE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                mediaListService.duplicate(
                        userId,
                        UUID.randomUUID(),
                        new DuplicateMediaListRequest("Cópia")
                )
        ).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exception.getCode()).isEqualTo("PRO_REQUIRED");
        });

        verify(mediaListRepository, never()).findWithOwnerById(any());
        verify(mediaListItemRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void deletesOwnedListAndItsItemsAndLikes() {
        UUID userId = UUID.randomUUID();
        MediaList list = mediaList("Descartáveis");
        list.getOwner().setId(userId);
        when(mediaListRepository.findWithOwnerById(list.getId()))
                .thenReturn(Optional.of(list));

        mediaListService.delete(userId, list.getId());

        verify(mediaListLikeRepository).deleteByListId(list.getId());
        verify(mediaListItemRepository).deleteByListId(list.getId());
        verify(mediaListRepository).delete(list);
    }

    @Test
    void allowsProOwnerToSetAListBackdrop() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        owner.setActive(true);
        owner.setAccountTier(AccountTier.PRO);
        MediaList list = mediaList("Cinema");
        list.setOwner(owner);
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);
        media.setTitle("Paris, Texas");

        when(mediaListRepository.findWithOwnerById(list.getId()))
                .thenReturn(Optional.of(list));
        when(mediaListItemRepository.existsByListIdAndMediaId(list.getId(), mediaId))
                .thenReturn(true);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(userMediaArtworkService.findOptions(userId, mediaId))
                .thenReturn(artworkOptions(mediaId, List.of(
                        new ArtworkOptionResponse(
                                "tmdb:backdrop:/clean.jpg",
                                "https://images.example/clean.jpg",
                                "https://images.example/clean-preview.jpg",
                                1280,
                                720,
                                null,
                                null
                        ),
                        new ArtworkOptionResponse(
                                "tmdb:backdrop:/pt.jpg",
                                "https://images.example/pt.jpg",
                                "https://images.example/pt-preview.jpg",
                                1280,
                                720,
                                "pt",
                                null
                        )
                )));
        when(mediaListRepository.saveAndFlush(any(MediaList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaListItemRepository.findRecentCoversByListIds(List.of(list.getId())))
                .thenReturn(List.of());

        MediaListResponse response = mediaListService.update(
                userId,
                list.getId(),
                new UpdateMediaListRequest(
                        "Cinema",
                        null,
                        Visibility.PUBLIC,
                        true,
                        null,
                        mediaId,
                        "tmdb:backdrop:/clean.jpg"
                )
        );

        assertThat(response.backdropUrl())
                .isEqualTo("https://images.example/clean.jpg");
        assertThat(list.getBackdropMedia()).isSameAs(media);
        assertThat(list.getBackdropKey()).isEqualTo("tmdb:backdrop:/clean.jpg");
    }

    @Test
    void rejectsListBackdropForFreeOwner() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        owner.setActive(true);
        owner.setAccountTier(AccountTier.FREE);
        MediaList list = mediaList("Cinema");
        list.setOwner(owner);
        when(mediaListRepository.findWithOwnerById(list.getId()))
                .thenReturn(Optional.of(list));

        assertThatThrownBy(() -> mediaListService.update(
                userId,
                list.getId(),
                new UpdateMediaListRequest(
                        "Cinema",
                        null,
                        Visibility.PUBLIC,
                        true,
                        null,
                        mediaId,
                        "tmdb:backdrop:/clean.jpg"
                )
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(
                    org.springframework.http.HttpStatus.FORBIDDEN);
            assertThat(exception.getCode()).isEqualTo("PRO_REQUIRED");
        });

        verify(mediaListRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsListBackdropThatHasALanguage() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        owner.setActive(true);
        owner.setAccountTier(AccountTier.PRO);
        MediaList list = mediaList("Cinema");
        list.setOwner(owner);
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);

        when(mediaListRepository.findWithOwnerById(list.getId()))
                .thenReturn(Optional.of(list));
        when(mediaListItemRepository.existsByListIdAndMediaId(list.getId(), mediaId))
                .thenReturn(true);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(userMediaArtworkService.findOptions(userId, mediaId))
                .thenReturn(artworkOptions(mediaId, List.of(
                        new ArtworkOptionResponse(
                                "tmdb:backdrop:/pt.jpg",
                                "https://images.example/pt.jpg",
                                "https://images.example/pt-preview.jpg",
                                1280,
                                720,
                                "pt",
                                null
                        )
                )));

        assertThatThrownBy(() -> mediaListService.update(
                userId,
                list.getId(),
                new UpdateMediaListRequest(
                        "Cinema",
                        null,
                        Visibility.PUBLIC,
                        true,
                        null,
                        mediaId,
                        "tmdb:backdrop:/pt.jpg"
                )
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("INVALID_LIST_BACKDROP"));

        verify(mediaListRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsOnlyOwnersListsWithAggregatedItemCounts() {
        UUID userId = UUID.randomUUID();
        MediaList first = mediaList("Primeira");
        MediaList second = mediaList("Segunda");
        MediaListItemRepository.MediaListItemCount count =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListItemCount.class);
        MediaListItemRepository.MediaListCover recentCover =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListCover.class);
        MediaListItemRepository.MediaListCover olderCover =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListCover.class);

        when(mediaListRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(first, second));
        when(mediaListItemRepository.countByListIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(count));
        when(count.getListId()).thenReturn(first.getId());
        when(count.getItemCount()).thenReturn(3L);
        when(mediaListItemRepository.findRecentCoversByListIds(
                List.of(first.getId(), second.getId())
        )).thenReturn(List.of(recentCover, olderCover));
        when(recentCover.getListId()).thenReturn(first.getId());
        when(recentCover.getCoverUrl()).thenReturn("https://example.com/recent.jpg");
        when(recentCover.getType()).thenReturn(MediaType.MOVIE);
        when(olderCover.getListId()).thenReturn(first.getId());
        when(olderCover.getCoverUrl()).thenReturn("https://example.com/older.jpg");
        when(olderCover.getType()).thenReturn(MediaType.BOOK);

        List<MediaListResponse> response = mediaListService.findMine(userId);

        assertThat(response).extracting(MediaListResponse::name)
                .containsExactly("Primeira", "Segunda");
        assertThat(response).extracting(MediaListResponse::itemCount)
                .containsExactly(3L, 0L);
        assertThat(response.getFirst().previewItems())
                .extracting(item -> item.coverUrl(), item -> item.type())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "https://example.com/recent.jpg", MediaType.MOVIE),
                        org.assertj.core.groups.Tuple.tuple(
                                "https://example.com/older.jpg", MediaType.BOOK)
                );
        assertThat(response.get(1).previewItems()).isEmpty();
        verify(mediaListRepository).findAllByOwnerIdOrderByUpdatedAtDesc(userId);
    }

    @Test
    void doesNotQueryItemCountsWhenOwnerHasNoLists() {
        UUID userId = UUID.randomUUID();
        when(mediaListRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of());

        assertThat(mediaListService.findMine(userId)).isEmpty();

        verify(mediaListItemRepository, never()).countByListIds(any());
    }

    @Test
    void listsOnlyPublicListsContainingMedia() {
        UUID mediaId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("maria");
        owner.setDisplayName("Maria");

        MediaList list = mediaList("Cinema de estrada");
        list.setOwner(owner);
        MediaListItem item = new MediaListItem();
        item.setList(list);
        item.setPosition(4);
        MediaListItemRepository.MediaListPopularity membership =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListPopularity.class);

        MediaListItemRepository.MediaListItemCount count =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListItemCount.class);
        when(mediaListItemRepository.findAllByMediaIdAndListVisibility(
                mediaId,
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(List.of(membership), PageRequest.of(0, 20), 1));
        when(membership.getItem()).thenReturn(item);
        when(membership.getListId()).thenReturn(list.getId());
        when(membership.getLikeCount()).thenReturn(8L);
        when(mediaListRepository.findAllWithOwnerByIdIn(List.of(list.getId())))
                .thenReturn(List.of(list));
        when(mediaListItemRepository.countByListIds(List.of(list.getId())))
                .thenReturn(List.of(count));
        when(count.getListId()).thenReturn(list.getId());
        when(count.getItemCount()).thenReturn(12L);
        MediaListItemRepository.MediaListCover cover =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListCover.class);
        when(mediaListItemRepository.findRecentCoversByListIds(List.of(list.getId())))
                .thenReturn(List.of(cover));
        when(cover.getListId()).thenReturn(list.getId());
        when(cover.getCoverUrl()).thenReturn("https://example.com/latest.jpg");
        when(cover.getType()).thenReturn(MediaType.MOVIE);

        var response = mediaListService.findPublicByMedia(mediaId, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items())
                .extracting(PublicMediaListResponse::name)
                .containsExactly("Cinema de estrada");
        assertThat(response.items().getFirst().itemCount()).isEqualTo(12);
        assertThat(response.items().getFirst().likeCount()).isEqualTo(8);
        assertThat(response.items().getFirst().mediaPosition()).isEqualTo(4);
        assertThat(response.items().getFirst().previewItems().getFirst().coverUrl())
                .isEqualTo("https://example.com/latest.jpg");
        assertThat(response.items().getFirst().previewItems().getFirst().type())
                .isEqualTo(MediaType.MOVIE);
        assertThat(response.items().getFirst().owner().username()).isEqualTo("maria");
        verify(mediaListItemRepository).findAllByMediaIdAndListVisibility(
                mediaId,
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );
    }

    @Test
    void limitsPopularListsForMediaToThree() {
        UUID mediaId = UUID.randomUUID();
        when(mediaListItemRepository.findAllByMediaIdAndListVisibility(
                mediaId,
                Visibility.PUBLIC,
                PageRequest.of(0, 3)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 3), 0));

        assertThat(mediaListService.findPopularByMedia(mediaId)).isEmpty();

        verify(mediaListItemRepository).findAllByMediaIdAndListVisibility(
                mediaId,
                Visibility.PUBLIC,
                PageRequest.of(0, 3)
        );
    }

    @Test
    void searchesOnlyPublicListsAndAggregatesTheirCommunityData() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("maria");
        owner.setDisplayName("Maria");
        MediaList list = mediaList("Cinema de estrada");
        list.setOwner(owner);

        MediaListItemRepository.MediaListItemCount itemCount =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListItemCount.class);
        MediaListLikeRepository.MediaListLikeCount likeCount =
                org.mockito.Mockito.mock(MediaListLikeRepository.MediaListLikeCount.class);
        MediaListItemRepository.MediaListCover cover =
                org.mockito.Mockito.mock(MediaListItemRepository.MediaListCover.class);

        when(mediaListRepository.searchPublicLists(
                "cinema",
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(List.of(list), PageRequest.of(0, 20), 1));
        when(mediaListItemRepository.countByListIds(List.of(list.getId())))
                .thenReturn(List.of(itemCount));
        when(itemCount.getListId()).thenReturn(list.getId());
        when(itemCount.getItemCount()).thenReturn(12L);
        when(mediaListLikeRepository.countByListIds(List.of(list.getId())))
                .thenReturn(List.of(likeCount));
        when(likeCount.getListId()).thenReturn(list.getId());
        when(likeCount.getLikeCount()).thenReturn(8L);
        when(mediaListItemRepository.findRecentCoversByListIds(List.of(list.getId())))
                .thenReturn(List.of(cover));
        when(cover.getListId()).thenReturn(list.getId());
        when(cover.getCoverUrl()).thenReturn("https://example.com/cinema.jpg");
        when(cover.getType()).thenReturn(MediaType.MOVIE);

        var response = mediaListService.searchPublic("  cinema  ", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items().getFirst().name()).isEqualTo("Cinema de estrada");
        assertThat(response.items().getFirst().itemCount()).isEqualTo(12);
        assertThat(response.items().getFirst().likeCount()).isEqualTo(8);
        assertThat(response.items().getFirst().owner().username()).isEqualTo("maria");
        assertThat(response.items().getFirst().previewItems().getFirst().coverUrl())
                .isEqualTo("https://example.com/cinema.jpg");
        verify(mediaListRepository).searchPublicLists(
                "cinema",
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );
    }

    @Test
    void returnsGloballyPopularPublicListsInRepositoryOrder() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("maria");
        owner.setDisplayName("Maria");
        MediaList first = mediaList("Mais curtida");
        first.setOwner(owner);
        MediaList second = mediaList("Segunda");
        second.setOwner(owner);
        var firstPopularity = org.mockito.Mockito.mock(
                MediaListRepository.PopularListProjection.class);
        var secondPopularity = org.mockito.Mockito.mock(
                MediaListRepository.PopularListProjection.class);
        var firstCount = org.mockito.Mockito.mock(
                MediaListItemRepository.MediaListItemCount.class);

        when(firstPopularity.getListId()).thenReturn(first.getId());
        when(firstPopularity.getLikeCount()).thenReturn(12L);
        when(secondPopularity.getListId()).thenReturn(second.getId());
        when(secondPopularity.getLikeCount()).thenReturn(7L);
        when(mediaListRepository.findPopularPublicLists(
                Visibility.PUBLIC, PageRequest.of(0, 12)))
                .thenReturn(List.of(firstPopularity, secondPopularity));
        when(mediaListRepository.findAllWithOwnerByIdIn(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(second, first));
        when(mediaListItemRepository.countByListIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(firstCount));
        when(firstCount.getListId()).thenReturn(first.getId());
        when(firstCount.getItemCount()).thenReturn(5L);

        List<PublicListSearchResponse> response = mediaListService.findGloballyPopular(12);

        assertThat(response).extracting(PublicListSearchResponse::name)
                .containsExactly("Mais curtida", "Segunda");
        assertThat(response).extracting(PublicListSearchResponse::likeCount)
                .containsExactly(12L, 7L);
        assertThat(response).extracting(PublicListSearchResponse::itemCount)
                .containsExactly(5L, 0L);
    }

    @Test
    void returnsPublicDetailsToAnonymousVisitor() {
        UUID listId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("maria");
        owner.setDisplayName("Maria");
        MediaList list = mediaList("Cinema de estrada");
        list.setId(listId);
        list.setOwner(owner);

        when(mediaListRepository.findWithOwnerById(listId)).thenReturn(Optional.of(list));
        when(mediaListItemRepository.findAllWithMediaByListId(listId))
                .thenReturn(List.of());
        when(mediaListLikeRepository.countByListId(listId)).thenReturn(4L);

        var response = mediaListService.findAccessibleDetails(null, listId);

        assertThat(response.name()).isEqualTo("Cinema de estrada");
        assertThat(response.likeCount()).isEqualTo(4);
        assertThat(response.liked()).isFalse();
        assertThat(response.ownList()).isFalse();
        assertThat(response.owner().username()).isEqualTo("maria");
    }

    @Test
    void usesListOwnersCustomCoverForPublicItems() {
        UUID listId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        MediaList list = mediaList("Cinema");
        list.setId(listId);
        list.setOwner(owner);
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle("Ainda Estou Aqui");
        media.setCoverUrl("https://images.example/default.jpg");
        MediaListItem item = new MediaListItem();
        item.setId(UUID.randomUUID());
        item.setList(list);
        item.setMedia(media);
        item.setPosition(1);

        when(mediaListRepository.findWithOwnerById(listId))
                .thenReturn(Optional.of(list));
        when(mediaListItemRepository.findAllWithMediaByListId(listId))
                .thenReturn(List.of(item));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                List.of(media.getId()))).thenReturn(List.of());
        when(userArtworkResolver.resolve(owner.getId(), List.of(media)))
                .thenReturn(Map.of(
                        media.getId(),
                        new UserArtworkResolver.ResolvedArtwork(
                                "https://images.example/custom.jpg",
                                null,
                                true,
                                false
                        )
                ));
        MediaListItemRepository.MediaListConsumptionCount consumption =
                mock(MediaListItemRepository.MediaListConsumptionCount.class);
        when(consumption.getListId()).thenReturn(listId);
        when(consumption.getConsumedItemCount()).thenReturn(1L);
        when(mediaListItemRepository.countConsumedByListIds(
                viewerId,
                List.of(listId),
                com.scriptles.cabinet.user.enums.UserMediaStatus.COMPLETED
        )).thenReturn(List.of(consumption));
        when(mediaListItemRepository.findConsumedMediaIds(
                viewerId,
                listId,
                com.scriptles.cabinet.user.enums.UserMediaStatus.COMPLETED
        )).thenReturn(List.of(media.getId()));

        var response = mediaListService.findAccessibleDetails(viewerId, listId);

        assertThat(response.items().getFirst().coverUrl())
                .isEqualTo("https://images.example/custom.jpg");
        assertThat(response.items().getFirst().consumed()).isTrue();
        assertThat(response.consumedItemCount()).isEqualTo(1);
        assertThat(response.consumedPercentage()).isEqualTo(100);
        verify(userArtworkResolver).resolve(owner.getId(), List.of(media));
    }

    @Test
    void hidesPrivateDetailsFromOtherUsers() {
        UUID listId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        MediaList list = mediaList("Lista privada");
        list.setId(listId);
        list.setOwner(owner);
        list.setVisibility(Visibility.PRIVATE);
        when(mediaListRepository.findWithOwnerById(listId)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> mediaListService.findAccessibleDetails(
                UUID.randomUUID(),
                listId
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("Lista não encontrada");

        verify(mediaListItemRepository, never()).findAllWithMediaByListId(listId);
    }

    @Test
    void addsMediaAtTheEndOfAnOwnedList() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaList list = mediaList("Favoritos");
        list.getOwner().setId(userId);
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);
        media.setTitle("Paris, Texas");

        when(mediaListRepository.findWithOwnerById(list.getId()))
                .thenReturn(Optional.of(list));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(mediaListItemRepository.existsByListIdAndMediaId(list.getId(), mediaId))
                .thenReturn(false);
        when(mediaListItemRepository.findMaxPositionByListId(list.getId())).thenReturn(2);
        when(mediaListItemRepository.saveAndFlush(any(MediaListItem.class)))
                .thenAnswer(invocation -> {
                    MediaListItem item = invocation.getArgument(0);
                    item.setId(UUID.randomUUID());
                    return item;
                });
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(List.of(mediaId)))
                .thenReturn(List.of());

        MediaListItemResponse response = mediaListService.addItem(
                userId,
                list.getId(),
                new AddMediaListItemRequest(mediaId, "  Rever com calma.  ")
        );

        ArgumentCaptor<MediaListItem> captor = ArgumentCaptor.forClass(MediaListItem.class);
        verify(mediaListItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getList()).isSameAs(list);
        assertThat(captor.getValue().getMedia()).isSameAs(media);
        assertThat(captor.getValue().getPosition()).isEqualTo(3);
        assertThat(captor.getValue().getNotes()).isEqualTo("Rever com calma.");
        assertThat(response.title()).isEqualTo("Paris, Texas");
        assertThat(response.position()).isEqualTo(3);
        verify(mediaListRepository).save(list);
    }

    @Test
    void rejectsMediaAlreadyInTheList() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaList list = mediaList("Favoritos");
        list.getOwner().setId(userId);
        Media media = new Media();
        media.setId(mediaId);

        when(mediaListRepository.findWithOwnerById(list.getId()))
                .thenReturn(Optional.of(list));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(mediaListItemRepository.existsByListIdAndMediaId(list.getId(), mediaId))
                .thenReturn(true);

        assertThatThrownBy(() -> mediaListService.addItem(
                userId,
                list.getId(),
                new AddMediaListItemRequest(mediaId, null)
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("Esta mídia já está na lista");

        verify(mediaListItemRepository, never()).saveAndFlush(any());
    }

    private MediaList mediaList(String name) {
        MediaList list = new MediaList();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        list.setOwner(owner);
        list.setId(UUID.randomUUID());
        list.setName(name);
        list.setVisibility(Visibility.PUBLIC);
        list.setOrdered(true);
        return list;
    }

    private ArtworkOptionsResponse artworkOptions(
            UUID mediaId,
            List<ArtworkOptionResponse> backdropOptions
    ) {
        return new ArtworkOptionsResponse(
                mediaId,
                ArtworkProvider.TMDB,
                null,
                null,
                null,
                null,
                List.of(),
                backdropOptions
        );
    }
}
