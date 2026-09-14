package com.scriptles.cabinet.catalog.api;
import java.util.*;
public record FranchiseResponse(UUID id,String slug,String name,String originalName,String description,String type,String status,String posterUrl,String backdropUrl,FranchiseSummaryResponse parent,List<FranchiseSummaryResponse> children,List<CollectionSummaryResponse> collections,List<MediaSummaryResponse> media,Map<String,ProgressResponse> progress) {
 public record MediaSummaryResponse(UUID id,String title,String type,String coverUrl) {}
 public record ProgressResponse(int completedCount,int totalCount) {}
}
