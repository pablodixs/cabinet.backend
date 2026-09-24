package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.profile.service.HQReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController @RequestMapping("/v1/profiles/{handle}/hq-reviews") @RequiredArgsConstructor
public class HQStandalonePublicReviewController {
    private final HQReviewService reviews;
    @GetMapping public List<HQReviewService.ReviewView> publicReviews(@PathVariable String handle) { return reviews.publicReviews(handle); }
}
