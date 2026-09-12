package com.scriptles.cabinet.media.dto.response;

import java.util.List;

public record AlbumTracksResponse(List<ExternalMediaDetailsResponse.TrackResponse> tracks) {}
