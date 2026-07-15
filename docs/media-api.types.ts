export type ExternalSource =
  | "TMDB" | "IMDB" | "GOOGLE_BOOKS" | "OPEN_LIBRARY"
  | "MUSICBRAINZ" | "SPOTIFY" | "APPLE_MUSIC" | "DEEZER"
  | "LAST_FM" | "WIKIDATA" | "JUSTWATCH" | "MANUAL";

export type MediaType = "BOOK" | "MOVIE" | "SERIES" | "TRACK" | "ALBUM";

export interface Genre {
  id: string | null;
  name: string;
  source: ExternalSource;
}

export interface ExternalMediaSearchResult {
  id: string | null;
  externalId: string;
  source: ExternalSource;
  type: MediaType;
  title: string;
  creator: string | null;
  description: string | null;
  coverUrl: string | null;
  releaseDate: string | null;
  durationSeconds: number | null;
  wikidataId: string | null;
  imported: boolean;
}

export type MediaSearchSort = "RELEVANCE" | "RATING";

export interface MediaSearchResult {
  id: string | null;
  externalId: string;
  source: ExternalSource;
  type: Extract<MediaType, "MOVIE" | "SERIES" | "ALBUM">;
  title: string;
  creator: string | null;
  description: string | null;
  coverUrl: string | null;
  releaseDate: string | null;
  imported: boolean;
  averageRating: number | null;
  ratingCount: number;
}

export interface MediaSearchPage {
  items: MediaSearchResult[];
  nextCursor: string | null;
}

interface MediaDetailsBase<T extends MediaType, D> {
  id: string | null;
  externalId: string;
  source: ExternalSource;
  type: T;
  title: string;
  originalTitle: string | null;
  creator: string | null;
  description: string | null;
  tagline: string | null;
  coverUrl: string | null;
  backdropUrl: string | null;
  logoUrl: string | null;
  externalUrl: string | null;
  releaseDate: string | null;
  originalLanguage: string | null;
  countryCode: string | null;
  wikidataId: string | null;
  externalReferences: Record<string, string>;
  genres: Genre[];
  imported: boolean;
  likeCount: number;
  averageRating: number | null;
  listCount: number;
  completedCount: number;
  details: D;
}

export interface MovieSpecificDetails {
  runtimeMinutes: number | null;
  budget: number | null;
  revenue: number | null;
  director: string | null;
}

export interface TrackSpecificDetails {
  durationSeconds: number | null;
  explicit: boolean | null;
}

export interface AlbumTrack {
  externalId: string | null;
  title: string;
  discNumber: number | null;
  trackNumber: number | null;
  durationSeconds: number | null;
  explicit: boolean | null;
}

export interface AlbumSpecificDetails {
  albumType: string | null;
  numberOfTracks: number | null;
  tracks: AlbumTrack[];
}

export interface SeasonSummary {
  externalId: string | null;
  seasonNumber: number;
  name: string | null;
  description: string | null;
  coverUrl: string | null;
  episodeCount: number | null;
  airDate: string | null;
}

export interface SeriesSpecificDetails {
  status: string | null;
  numberOfSeasons: number | null;
  numberOfEpisodes: number | null;
  lastAirDate: string | null;
  seasons: SeasonSummary[];
}

export interface BookSpecificDetails {
  isbn10: string | null;
  isbn13: string | null;
  pageCount: number | null;
  publisher: string | null;
  canonicalWorkWikidataId: string | null;
}

export type MediaRelationType =
  | "ADAPTATION_OF" | "ADAPTED_AS" | "SOUNDTRACK" | "SOUNDTRACK_OF";

export interface RelatedMedia {
  id: string | null;
  relationType: MediaRelationType;
  type: "BOOK" | "MOVIE" | "SERIES" | "ALBUM";
  title: string;
  releaseDate: string | null;
  coverUrl: string | null;
  wikidataId: string;
  providerSource: ExternalSource | null;
  providerExternalId: string | null;
  externalUrl: string;
  imported: boolean;
}

export interface RelatedMediaResponse {
  source: "WIKIDATA";
  incomplete: boolean;
  items: RelatedMedia[];
}

export type MovieDetailsResponse = MediaDetailsBase<"MOVIE", MovieSpecificDetails>;
export type TrackDetailsResponse = MediaDetailsBase<"TRACK", TrackSpecificDetails>;
export type AlbumDetailsResponse = MediaDetailsBase<"ALBUM", AlbumSpecificDetails>;
export type SeriesDetailsResponse = MediaDetailsBase<"SERIES", SeriesSpecificDetails>;
export type BookDetailsResponse = MediaDetailsBase<"BOOK", BookSpecificDetails>;

export type MediaDetailsResponse =
  | MovieDetailsResponse
  | TrackDetailsResponse
  | AlbumDetailsResponse
  | SeriesDetailsResponse
  | BookDetailsResponse;

export interface Episode {
  externalId: string | null;
  episodeNumber: number;
  title: string;
  description: string | null;
  stillUrl: string | null;
  airDate: string | null;
  runtimeMinutes: number | null;
}

export interface SeasonEpisodesResponse {
  seriesExternalId: string;
  seasonNumber: number;
  episodes: Episode[];
}

export interface MediaLikeResponse {
  liked: boolean;
}

export interface LinkWikidataRequest {
  wikidataId: `Q${number}`;
  language?: string;
}

export interface WikidataLinkResponse {
  mediaId: string;
  wikidataId: `Q${number}`;
  externalUrl: string;
  enriched: boolean;
}
