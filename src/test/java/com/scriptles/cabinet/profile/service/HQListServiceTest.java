package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.profile.entity.HQList;
import com.scriptles.cabinet.profile.entity.HQOperator;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQListItemRepository;
import com.scriptles.cabinet.profile.repository.HQListRepository;
import com.scriptles.cabinet.profile.repository.HQOperatorRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HQListServiceTest {
    @Mock HQListRepository lists;
    @Mock HQListItemRepository items;
    @Mock HQProfileRepository hqs;
    @Mock HQOperatorRepository operators;
    @Mock MediaRepository media;
    @InjectMocks HQListService service;

    @Test void ownerCreatesListOwnedByHQWithoutPersonalAccount() {
        UUID hqId = UUID.randomUUID();
        HQProfile hq = new HQProfile(); hq.setId(hqId); hq.setClaimStatus(HQClaimStatus.CLAIMED);
        when(hqs.findById(hqId)).thenReturn(Optional.of(hq));
        when(lists.save(any(HQList.class))).thenAnswer(invocation -> {
            HQList list = invocation.getArgument(0); list.setId(UUID.randomUUID()); return list;
        });
        AuthenticatedHQ owner = actor(hqId, HQMemberRole.OWNER);
        var created = service.create(owner, new HQListService.ListInput("Filmes favoritos", null, Visibility.PUBLIC, true, null));
        assertThat(created.name()).isEqualTo("Filmes favoritos");
        assertThat(created.editorIds()).isEmpty();
        org.mockito.ArgumentCaptor<HQList> capture = org.mockito.ArgumentCaptor.forClass(HQList.class);
        verify(lists).save(capture.capture());
        assertThat(capture.getValue().getHqProfile()).isSameAs(hq);
    }

    @Test void editorMustBeAssignedToEditSpecificList() {
        UUID hqId = UUID.randomUUID(); UUID listId = UUID.randomUUID();
        HQList list = list(hqId, listId);
        AuthenticatedHQ editor = actor(hqId, HQMemberRole.EDITOR);
        when(lists.findByIdAndHqProfileId(listId, hqId)).thenReturn(Optional.of(list));
        var input = new HQListService.ListInput("Nova curadoria", null, Visibility.PUBLIC, true, null);
        assertThatThrownBy(() -> service.update(editor, listId, input)).isInstanceOf(ApiException.class);
        HQOperator operator = new HQOperator(); operator.setId(editor.operatorId()); operator.setActive(true);
        list.getEditors().add(operator);
        assertThat(service.update(editor, listId, input).name()).isEqualTo("Nova curadoria");
    }

    @Test void cannotAssociateEditorFromAnotherHQ() {
        UUID hqId = UUID.randomUUID(); UUID listId = UUID.randomUUID(); UUID editorId = UUID.randomUUID();
        HQList list = list(hqId, listId);
        HQProfile other = new HQProfile(); other.setId(UUID.randomUUID());
        HQOperator editor = new HQOperator(); editor.setId(editorId); editor.setActive(true); editor.setRole(HQMemberRole.EDITOR); editor.setHqProfile(other);
        when(lists.findByIdAndHqProfileId(listId, hqId)).thenReturn(Optional.of(list));
        when(operators.findById(editorId)).thenReturn(Optional.of(editor));
        assertThatThrownBy(() -> service.assignEditor(actor(hqId, HQMemberRole.OWNER), listId, editorId)).isInstanceOf(ApiException.class);
        assertThat(list.getEditors()).isEmpty();
    }

    @Test void onlyPublicListsAreReturnedToVisitors() {
        UUID hqId = UUID.randomUUID();
        HQProfile hq = new HQProfile(); hq.setId(hqId);
        when(hqs.findByProfileHandleIgnoreCase("studio")).thenReturn(Optional.of(hq));
        when(lists.findByHqProfileIdAndVisibilityOrderByUpdatedAtDesc(hqId, Visibility.PUBLIC)).thenReturn(List.of());
        assertThat(service.publicLists("studio")).isEmpty();
        verify(lists, never()).findByHqProfileIdOrderByUpdatedAtDesc(hqId);
    }

    private HQList list(UUID hqId, UUID listId) {
        HQProfile hq = new HQProfile(); hq.setId(hqId);
        HQList list = new HQList(); list.setId(listId); list.setHqProfile(hq); list.setName("Original");
        return list;
    }
    private AuthenticatedHQ actor(UUID hqId, HQMemberRole role) {
        return new AuthenticatedHQ(UUID.randomUUID(), hqId, UUID.randomUUID(), "editor@example.com", "Editor", role, true);
    }
}
