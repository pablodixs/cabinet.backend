package com.scriptles.cabinet.profile.dto;
import com.scriptles.cabinet.profile.enums.*; import java.util.*;
import tools.jackson.databind.JsonNode;
public record ProfileResponse(UUID id, ProfileType type, String handle, String displayName, String avatarUrl, String backdropUrl, String bio, boolean verified, long followers, long following, HQDetails hq, List<String> navigation, List<String> catalogSections, Viewer viewer, JsonNode richBio) {
 public ProfileResponse(UUID id, ProfileType type, String handle, String displayName, String avatarUrl, String backdropUrl, String bio, boolean verified, long followers, long following, HQDetails hq, List<String> navigation, List<String> catalogSections, Viewer viewer) { this(id,type,handle,displayName,avatarUrl,backdropUrl,bio,verified,followers,following,hq,navigation,catalogSections,viewer,null); }
 public record HQDetails(UUID id,HQType type,String websiteUrl,String countryCode,HQClaimStatus claimStatus){}
 public record Viewer(boolean following,boolean canManage,boolean canClaim){}
}
