package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.profile.service.HQListService;
import com.scriptles.cabinet.profile.service.HQOperatorService;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/hq-console/lists") @RequiredArgsConstructor
public class HQStandaloneListController {
    private final HQListService lists;
    private final HQOperatorService operators;
    private AuthenticatedHQ current(AuthenticatedHQ actor) { return operators.refresh(actor.operatorId()); }

    @GetMapping public List<HQListService.ListView> mine(@AuthenticationPrincipal AuthenticatedHQ actor) { return lists.mine(current(actor)); }
    @PostMapping public HQListService.ListView create(@AuthenticationPrincipal AuthenticatedHQ actor, @Valid @RequestBody HQListService.ListInput input) { return lists.create(current(actor), input); }
    @PutMapping("/{listId}") public HQListService.ListView update(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID listId, @Valid @RequestBody HQListService.ListInput input) { return lists.update(current(actor), listId, input); }
    @DeleteMapping("/{listId}") public void delete(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID listId) { lists.delete(current(actor), listId); }
    @PutMapping("/{listId}/editors/{operatorId}") public HQListService.ListView assign(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID listId, @PathVariable UUID operatorId) { return lists.assignEditor(current(actor), listId, operatorId); }
    @DeleteMapping("/{listId}/editors/{operatorId}") public HQListService.ListView unassign(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID listId, @PathVariable UUID operatorId) { return lists.removeEditor(current(actor), listId, operatorId); }
    @PostMapping("/{listId}/items") public HQListService.ListView addItem(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID listId, @Valid @RequestBody HQListService.ItemInput input) { return lists.addItem(current(actor), listId, input); }
    @DeleteMapping("/{listId}/items/{itemId}") public HQListService.ListView removeItem(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID listId, @PathVariable UUID itemId) { return lists.removeItem(current(actor), listId, itemId); }
}
