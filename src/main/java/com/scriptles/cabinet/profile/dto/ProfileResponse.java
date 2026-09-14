package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.*; import java.util.*;
public record ProfileResponse(UUID id, ProfileType type, String handle, String displayName, String avatarUrl, String backdropUrl, String bio, boolean verified, long followers, long following, HQDetails hq, List<String> navigation, List<String> catalogSections, Viewer viewer) {
 public record HQDetails(UUID id,HQType type,String websiteUrl,String countryCode,HQClaimStatus claimStatus){}
 public record Viewer(boolean following,boolean canManage,boolean canClaim){}
}
