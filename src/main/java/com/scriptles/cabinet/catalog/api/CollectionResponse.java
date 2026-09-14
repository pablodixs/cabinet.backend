package com.scriptles.cabinet.catalog.api;
import java.util.*;
public record CollectionResponse(UUID id,String slug,String title,String originalTitle,String description,String type,String sourceMode,String status,String posterUrl,String backdropUrl,List<FranchiseSummaryResponse> franchises,List<SectionResponse> sections,List<ItemResponse> items,ViewerResponse viewer) {
 public record SectionResponse(UUID id,String key,String title,int position) {}
 public record ItemResponse(UUID id,UUID mediaId,String title,String type,String coverUrl,int position,UUID sectionId,String relationType) {}
 public record ViewerResponse(int completedCount,int totalCount,double completion,int ratedCount,Double averageRating) {}
}
