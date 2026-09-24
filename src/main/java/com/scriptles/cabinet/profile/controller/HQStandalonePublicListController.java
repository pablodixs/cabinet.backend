package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.profile.service.HQListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController @RequestMapping("/v1/profiles/{handle}/hq-lists") @RequiredArgsConstructor
public class HQStandalonePublicListController {
    private final HQListService lists;
    @GetMapping public List<HQListService.ListView> publicLists(@PathVariable String handle) { return lists.publicLists(handle); }
}
