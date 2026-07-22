export type ExternalSource =
  | "TMDB" | "IMDB" | "GOOGLE_BOOKS" | "OPEN_LIBRARY"
  | "MUSICBRAINZ" | "SPOTIFY" | "APPLE_MUSIC" | "DEEZER"
  | "LAST_FM" | "WIKIDATA" | "JUSTWATCH" | "OMDB"
  | "ROTTEN_TOMATOES" | "METACRITIC" | "MANUAL";

export type MediaType = "BOOK" | "MOVIE" | "SERIES" | "TRACK" | "ALBUM";

export type CreditRole =
  | "AUTHOR" | "CREATOR" | "DIRECTOR" | "ACTOR"
  | "ARTIST" | "COMPOSER" | "PRODUCER" | "SCREENWRITER";

export interface Genre {
  id: string | null;
  name: string;
  source: ExternalSource;
}

export interface MediaCredit {
  personId: string | null;
  name: string;
  role: CreditRole;
  characterName: string | null;
  position: number | null;
  imageUrl: string | null;
  source: ExternalSource;
  externalId: string | null;
}

export interface Artist {
  id: string;
  name: string;
  biography: string | null;
  imageUrl: string | null;
  source: ExternalSource;
  externalId: string | null;
  workCount: number;
  roles: CreditRole[];
}

export interface ArtistWorkCredit {
  role: CreditRole;
  characterName: string | null;
}

export interface ArtistWork {
  mediaId: string;
  type: MediaType;
  title: string;
  coverUrl: string | null;
  releaseDate: string | null;
  credits: ArtistWorkCredit[];
}

export type MoreByState = "READY" | "EMPTY" | "UNSUPPORTED";

export interface MoreByPerson {
  id: string;
  name: string;
  imageUrl: string | null;
}

export interface MoreByResponse {
  state: MoreByState;
  role: Extract<CreditRole, "DIRECTOR" | "ARTIST"> | null;
  person: MoreByPerson | null;
  incomplete: boolean;
  items: MediaSearchResult[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type MediaCreditsPage = PageResponse<MediaCredit>;

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
  type: Extract<MediaType, "MOVIE" | "SERIES" | "ALBUM" | "BOOK">;
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

export interface RankedMediaResult extends Omit<MediaSearchResult, "type"> {
  type: MediaType;
}

export type TopRatedMediaPage = PageResponse<RankedMediaResult>;

export interface TrendingMediaResponse {
  items: RankedMediaResult[];
  periodDays: number;
}

export type InterestTargetType = "GENRE" | "PERSON" | "MEDIA";
export type InterestPreference = "POSITIVE" | "NEGATIVE";

export interface InterestResponse {
  targetType: InterestTargetType;
  targetId: string;
  label: string;
  polarity: InterestPreference;
  explicitPreference: InterestPreference | null;
  inferred: boolean;
  strength: number;
}

export type InterestPage = PageResponse<InterestResponse>;

export interface UpsertInterestPreferenceRequest {
  targetType: InterestTargetType;
  targetId: string;
  preference: InterestPreference;
}

export interface InterestOption {
  targetType: InterestTargetType;
  targetId: string;
  label: string;
  subtitle: string | null;
  imageUrl: string | null;
}

export type RecommendationSource = "PERSONALIZED" | "TRENDING";
export type RecommendationReasonType = InterestTargetType | "TRENDING";

export interface RecommendationReason {
  targetType: RecommendationReasonType;
  targetId: string | null;
  label: string;
}

export interface RecommendationItem {
  media: RankedMediaResult;
  source: RecommendationSource;
  reasons: RecommendationReason[];
}

export interface RecommendationResponse {
  items: RecommendationItem[];
}

export type ExternalInfoSectionState =
  | "READY" | "EMPTY" | "PENDING" | "STALE"
  | "ERROR" | "NOT_CONFIGURED" | "NOT_SUPPORTED";

export type AwardResult = "WIN" | "NOMINATION";
export type AwardOrigin = "WIKIDATA" | "MANUAL";
export type AwardSubjectType = "MEDIA" | "PERSON";
export type AwardSectionState =
  | "PENDING" | "READY" | "EMPTY" | "STALE" | "ERROR" | "NOT_LINKED";
export type AwardDatePrecision = "YEAR" | "MONTH" | "DAY";

export interface AwardReference {
  qid: string | null;
  name: string | null;
}

export interface AwardItem {
  id: string;
  result: AwardResult;
  program: AwardReference | null;
  category: AwardReference;
  ceremony: AwardReference | null;
  eventDate: string | null;
  eventYear: number | null;
  datePrecision: AwardDatePrecision | null;
  work: {
    mediaId: string | null;
    wikidataId: string | null;
    title: string | null;
  } | null;
  origin: AwardOrigin;
  curated: boolean;
  sourceUrl: string | null;
}

export interface AwardPageResponse {
  subjectId: string;
  subjectType: AwardSubjectType;
  state: AwardSectionState;
  fetchedAt: string | null;
  expiresAt: string | null;
  totalWins: number;
  totalNominations: number;
  items: AwardItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ModerationAwardResponse {
  id: string;
  subjectType: AwardSubjectType;
  subjectId: string;
  result: AwardResult;
  programQid: string | null;
  programName: string | null;
  categoryQid: string | null;
  categoryName: string;
  ceremonyQid: string | null;
  ceremonyName: string | null;
  eventDate: string | null;
  eventYear: number | null;
  datePrecision: AwardDatePrecision | null;
  workQid: string | null;
  workName: string | null;
  workMediaId: string | null;
  origin: AwardOrigin;
  sourceStatementId: string | null;
  sourceUrl: string | null;
  curated: boolean;
  hidden: boolean;
  version: number;
}

export interface CreateAwardRequest {
  subjectType: AwardSubjectType;
  subjectId: string;
  result: AwardResult;
  programQid?: string | null;
  programName?: string | null;
  categoryQid?: string | null;
  categoryName: string;
  ceremonyQid?: string | null;
  ceremonyName?: string | null;
  eventDate?: string | null;
  eventYear?: number | null;
  datePrecision?: AwardDatePrecision | null;
  workQid?: string | null;
  workName?: string | null;
  sourceUrl?: string | null;
}

export interface UpdateAwardRequest {
  version: number;
  result: AwardResult;
  programQid?: string | null;
  programName?: string | null;
  categoryQid?: string | null;
  categoryName: string;
  ceremonyQid?: string | null;
  ceremonyName?: string | null;
  eventDate?: string | null;
  eventYear?: number | null;
  datePrecision?: AwardDatePrecision | null;
  workQid?: string | null;
  workName?: string | null;
  sourceUrl?: string | null;
  hidden: boolean;
}

export type ExternalOfferType =
  | "SUBSCRIPTION" | "FREE" | "ADS" | "RENT" | "BUY"
  | "STREAM" | "BUY_DOWNLOAD" | "BUY_PHYSICAL" | "FREE_DOWNLOAD";

export type ExternalRatingMetric = "IMDB_RATING" | "TOMATOMETER" | "METASCORE";

export interface MediaExternalOffer {
  dataSource: ExternalSource;
  providerId: string | null;
  providerName: string;
  logoUrl: string | null;
  type: ExternalOfferType;
  url: string | null;
  sourceUrl: string | null;
}

export interface MediaExternalRating {
  provider: "OMDB";
  source: Extract<ExternalSource, "IMDB" | "ROTTEN_TOMATOES" | "METACRITIC">;
  metric: ExternalRatingMetric;
  value: number;
  scale: number;
  displayValue: string | null;
  externalId: string | null;
}

export interface MediaExternalInfoResponse {
  mediaId: string;
  countryCode: string;
  availability: {
    state: ExternalInfoSectionState;
    fetchedAt: string | null;
    expiresAt: string | null;
    attributions: string[];
    offers: MediaExternalOffer[];
  };
  ratings: {
    state: ExternalInfoSectionState;
    fetchedAt: string | null;
    expiresAt: string | null;
    items: MediaExternalRating[];
  };
}

export interface MediaCommunityUser {
  id: string;
  username: string;
  avatarUrl: string | null;
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
  credits: MediaCredit[];
  imported: boolean;
  likeCount: number;
  recentLikers: MediaCommunityUser[];
  averageRating: number | null;
  listCount: number;
  completedCount: number;
  recentCompleters: MediaCommunityUser[];
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
  animatedCoverUrl: string | null;
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
  | "ADAPTATION_OF" | "ADAPTED_AS" | "SOUNDTRACK" | "SOUNDTRACK_OF"
  | "RE_RECORDING_OF" | "RE_RECORDED_AS";

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
  id: string | null;
  externalId: string | null;
  episodeNumber: number;
  title: string;
  description: string | null;
  stillUrl: string | null;
  airDate: string | null;
  runtimeMinutes: number | null;
  watched: boolean;
  unwatchedPreviousCount: number;
}

export interface SeasonEpisodesResponse {
  seriesExternalId: string;
  seasonNumber: number;
  episodes: Episode[];
}

export interface EpisodeAgendaItem {
  episodeId: string;
  seriesId: string;
  seriesTitle: string;
  seriesCoverUrl: string | null;
  seasonNumber: number;
  episodeNumber: number;
  episodeTitle: string;
  stillUrl: string | null;
  airDate: string;
  watched: boolean;
}

export interface EpisodeAgendaResponse {
  syncPending: boolean;
  lastSyncedAt: string | null;
  overdueCount: number;
  overdue: EpisodeAgendaItem[];
  upcoming: EpisodeAgendaItem[];
}

export interface EpisodeWatchResponse {
  episodeId: string;
  watched: boolean;
  watchedAt: string | null;
  changedEpisodeIds: string[];
  seriesStatus: "PLANNED" | "IN_PROGRESS" | "COMPLETED" | "PAUSED" | "DROPPED" | null;
}

export interface MediaLikeResponse {
  liked: boolean;
}

export type ReviewVisibility = "PUBLIC" | "PRIVATE";

export interface ReviewLikerResponse {
  id: string;
  username: string;
  avatarUrl: string | null;
}

export interface ReviewResponse {
  id: string;
  mediaId: string;
  rating: number | null;
  content: string | null;
  containsSpoilers: boolean;
  visibility: ReviewVisibility;
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  liked: boolean;
  recentLikers: ReviewLikerResponse[];
  author: {
    id: string;
    username: string;
    displayName: string;
    avatarUrl: string | null;
  };
  activityId: string | null;
}

export type DiaryEntryType = "LOGGED" | "RELOGGED" | "WATCHED" | "REWATCHED";

export interface CreateDiaryEntryRequest {
  mediaId: string;
  occurredOn: string;
  reconsumption?: boolean;
  rating?: number | null;
  review?: string | null;
  containsSpoilers?: boolean;
  visibility: ReviewVisibility;
  tags?: string[];
}

export interface DiaryEntryResponse {
  id: string;
  type: DiaryEntryType;
  occurredOn: string;
  loggedOn: string | null;
  mediaId: string;
  mediaType: MediaType;
  title: string;
  coverUrl: string | null;
  releaseDate: string | null;
  source: ExternalSource | null;
  externalId: string | null;
  rating: number | null;
  review: string | null;
  containsSpoilers: boolean;
  visibility: ReviewVisibility;
  tags: string[];
}

export interface PopularReviewResponse {
  review: ReviewResponse;
  media: RankedMediaResult;
}

export interface MediaListPreview {
  coverUrl: string;
  type: MediaType;
}

export interface PublicListSummary {
  id: string;
  name: string;
  description: string | null;
  ordered: boolean;
  coverUrl: string | null;
  previewItems: MediaListPreview[];
  itemCount: number;
  likeCount: number;
  updatedAt: string;
  owner: {
    id: string;
    username: string;
    displayName: string;
    avatarUrl: string | null;
  };
}

export interface ReviewLikeResponse {
  liked: boolean;
  likeCount: number;
  recentLikers: ReviewLikerResponse[];
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

export type AccountTier = "FREE" | "PRO";
export type ArtworkProvider = "TMDB" | "COVER_ART_ARCHIVE";

export interface ArtworkOption {
  key: string;
  url: string;
  previewUrl: string;
  width: number | null;
  height: number | null;
  language: string | null;
}

export interface ArtworkOptionsResponse {
  mediaId: string;
  provider: ArtworkProvider;
  defaultCoverUrl: string | null;
  defaultBackdropUrl: string | null;
  selectedCoverKey: string | null;
  selectedBackdropKey: string | null;
  coverOptions: ArtworkOption[];
  backdropOptions: ArtworkOption[];
}

export interface UpsertUserMediaArtworkRequest {
  coverKey: string | null;
  backdropKey: string | null;
}
