package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.RichTextDocument;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.profile.entity.HQList;
import com.scriptles.cabinet.profile.entity.HQListItem;
import com.scriptles.cabinet.profile.entity.HQOperator;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQListItemRepository;
import com.scriptles.cabinet.profile.repository.HQListRepository;
import com.scriptles.cabinet.profile.repository.HQOperatorRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class HQListService {
    private final HQListRepository lists;
    private final HQListItemRepository items;
    private final HQProfileRepository hqs;
    private final HQOperatorRepository operators;
    private final MediaRepository media;

    public record ListInput(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description,
                            @Size(max = 100000) String richDescription, @NotNull Visibility visibility,
                            boolean ordered, @Size(max = 500) String coverUrl) {
        public ListInput(String name, String description, Visibility visibility, boolean ordered, String coverUrl) {
            this(name, description, null, visibility, ordered, coverUrl);
        }
    }
    public record ItemInput(@NotNull UUID mediaId, @Size(max = 2000) String notes) {}
    public record ItemUpdateInput(@Size(max = 2000) String notes, @Min(0) Integer position) {}
    public record ItemView(UUID id, UUID mediaId, String title, String coverUrl, int position, String notes) {}
    public record ListView(UUID id, String name, String description, String richDescription, Visibility visibility, boolean ordered,
                           String coverUrl, Instant createdAt, Instant updatedAt, List<UUID> editorIds, List<ItemView> items) {}

    @Transactional(readOnly = true)
    public List<ListView> mine(AuthenticatedHQ actor) {
        return lists.findByHqProfileIdOrderByUpdatedAtDesc(actor.hqId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ListView> publicLists(String handle) {
        var hq = hqs.findByProfileHandleIgnoreCase(handle).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "HQ não encontrada"));
        return lists.findByHqProfileIdAndVisibilityOrderByUpdatedAtDesc(hq.getId(), Visibility.PUBLIC).stream().map(this::view)
                .map(list -> new ListView(list.id(), list.name(), list.description(), list.richDescription(), list.visibility(), list.ordered(),
                        list.coverUrl(), list.createdAt(), list.updatedAt(), List.of(), list.items())).toList();
    }

    @Transactional
    public ListView create(AuthenticatedHQ actor, ListInput input) {
        requireManager(actor);
        var hq = hqs.findById(actor.hqId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "HQ não encontrada"));
        if (hq.getClaimStatus() != HQClaimStatus.CLAIMED) throw error(HttpStatus.FORBIDDEN, "HQ_UNAVAILABLE", "HQ indisponível para publicação");
        validate(input);
        HQList list = new HQList(); list.setHqProfile(hq); apply(list, input);
        return view(lists.save(list));
    }

    @Transactional
    public ListView update(AuthenticatedHQ actor, UUID listId, ListInput input) {
        HQList list = owned(actor, listId); requireEditor(actor, list); validate(input);
        String existingRichDescription = list.getRichDescription();
        apply(list, input);
        if (input.richDescription() == null) list.setRichDescription(existingRichDescription);
        return view(list);
    }

    @Transactional
    public void delete(AuthenticatedHQ actor, UUID listId) {
        HQList list = owned(actor, listId); requireManager(actor);
        items.deleteAll(items.findByListIdOrderByPositionAsc(listId)); items.flush();
        list.getEditors().clear(); lists.delete(list);
    }

    @Transactional
    public ListView assignEditor(AuthenticatedHQ actor, UUID listId, UUID operatorId) {
        HQList list = owned(actor, listId); requireManager(actor);
        HQOperator editor = operators.findById(operatorId)
                .filter(o -> o.isActive() && o.getHqProfile().getId().equals(actor.hqId()) && o.getRole() == HQMemberRole.EDITOR)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "EDITOR_NOT_FOUND", "Editor da HQ não encontrado"));
        list.getEditors().add(editor);
        return view(list);
    }

    @Transactional
    public ListView removeEditor(AuthenticatedHQ actor, UUID listId, UUID operatorId) {
        HQList list = owned(actor, listId); requireManager(actor);
        list.getEditors().removeIf(operator -> operator.getId().equals(operatorId));
        return view(list);
    }

    @Transactional
    public ListView addItem(AuthenticatedHQ actor, UUID listId, ItemInput input) {
        HQList list = owned(actor, listId); requireEditor(actor, list);
        if (items.existsByListIdAndMediaId(listId, input.mediaId())) throw error(HttpStatus.CONFLICT, "MEDIA_ALREADY_LISTED", "Obra já incluída na lista");
        var selected = media.findById(input.mediaId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Obra não encontrada"));
        HQListItem item = new HQListItem(); item.setList(list); item.setMedia(selected);
        item.setPosition(items.findTopByListIdOrderByPositionDesc(listId).map(existing -> existing.getPosition() + 1).orElse(0)); item.setNotes(input.notes()); items.save(item);
        return view(list);
    }

    @Transactional
    public ListView removeItem(AuthenticatedHQ actor, UUID listId, UUID itemId) {
        HQList list = owned(actor, listId); requireEditor(actor, list);
        var item = items.findByIdAndListId(itemId, listId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Item não encontrado"));
        items.delete(item); items.flush();
        return view(list);
    }

    @Transactional
    public ListView updateItem(AuthenticatedHQ actor, UUID listId, UUID itemId, ItemUpdateInput input) {
        HQList list = owned(actor, listId); requireEditor(actor, list);
        HQListItem selected = items.findByIdAndListId(itemId, listId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Item não encontrado"));
        if (input.notes() != null) selected.setNotes(input.notes());
        if (input.position() != null) {
            List<HQListItem> ordered = new java.util.ArrayList<>(items.findByListIdOrderByPositionAsc(listId));
            ordered.remove(selected);
            int target = Math.min(input.position(), ordered.size());
            ordered.add(target, selected);
            for (int index = 0; index < ordered.size(); index++) ordered.get(index).setPosition(index);
        }
        return view(list);
    }

    private HQList owned(AuthenticatedHQ actor, UUID listId) {
        return lists.findByIdAndHqProfileId(listId, actor.hqId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "LIST_NOT_FOUND", "Lista não encontrada"));
    }
    private void requireManager(AuthenticatedHQ actor) {
        if (actor.role() != HQMemberRole.OWNER && actor.role() != HQMemberRole.ADMIN) throw error(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Permissão insuficiente");
    }
    private void requireEditor(AuthenticatedHQ actor, HQList list) {
        if (actor.role() == HQMemberRole.OWNER || actor.role() == HQMemberRole.ADMIN) return;
        if (actor.role() != HQMemberRole.EDITOR || list.getEditors().stream().noneMatch(o -> o.isActive() && o.getId().equals(actor.operatorId()))) {
            throw error(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Você não edita esta lista");
        }
    }
    private void validate(ListInput input) {
        if (input.visibility() != Visibility.PUBLIC && input.visibility() != Visibility.PRIVATE) throw error(HttpStatus.BAD_REQUEST, "INVALID_VISIBILITY", "Use visibilidade pública ou privada");
        if (input.richDescription() != null && !input.richDescription().isBlank()) {
            RichTextDocument.validateAndExtractText(input.richDescription(), true);
        }
    }
    private void apply(HQList list, ListInput input) {
        list.setName(input.name().trim()); list.setDescription(input.description()); list.setRichDescription(input.richDescription()); list.setVisibility(input.visibility());
        list.setOrdered(input.ordered()); list.setCoverUrl(input.coverUrl());
    }
    private ListView view(HQList list) {
        var content = items.findByListIdOrderByPositionAsc(list.getId()).stream()
                .map(item -> new ItemView(item.getId(), item.getMedia().getId(), item.getMedia().getTitle(), item.getMedia().getCoverUrl(), item.getPosition(), item.getNotes())).toList();
        return new ListView(list.getId(), list.getName(), list.getDescription(), list.getRichDescription(), list.getVisibility(), list.isOrdered(),
                list.getCoverUrl(), list.getCreatedAt(), list.getUpdatedAt(), list.getEditors().stream().map(HQOperator::getId).toList(), content);
    }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
