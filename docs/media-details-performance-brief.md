# Brief de performance — detalhes de mídia no backend

## Contexto

O endpoint principal de detalhes de mídia atualmente concentra dados públicos, métricas comunitárias e informações personalizadas do usuário em uma única resposta.

Isso aumenta o tempo de resposta, impede cache compartilhado entre usuários e faz com que consultas secundárias bloqueiem a exibição das informações principais da obra no frontend.

Endpoint atual:

```http
GET /v1/media/{mediaId}
```

Implementação principal:

```text
MediaQueryController.findDetails
MediaQueryService.findDetails
```

## Objetivo

Reduzir o tempo de resposta do conteúdo principal e permitir que o frontend entregue título, capa e descrição praticamente de forma imediata.

O backend deve:

- separar dados públicos de dados privados;
- retirar métricas pesadas do caminho crítico;
- permitir cache compartilhado dos detalhes públicos;
- carregar dados comunitários e pessoais por endpoints independentes;
- reduzir consultas SQL por requisição;
- oferecer invalidação previsível após mutações.

## Diagnóstico atual

`MediaQueryService.findDetails(mediaId, userId)` executa, na mesma operação:

- busca da mídia;
- busca de referências externas;
- resolução da referência principal;
- cálculo de métricas comunitárias;
- busca e transformação de créditos;
- resolução de artwork personalizado por usuário;
- busca de detalhes específicos por tipo;
- carregamento de faixas para álbuns;
- cálculo de estatísticas por faixa;
- carregamento de temporadas para séries;
- cálculo de estatísticas por temporada;
- contagem de curtidas, listas e conclusões;
- distribuição de avaliações;
- busca de usuários recentes.

A resposta também varia de acordo com o usuário autenticado por causa de:

- artwork personalizado;
- visibilidade de usuários recentes;
- avaliação pessoal de faixas;
- avaliação e progresso pessoal de temporadas.

Consequências:

- o endpoint não pode ser cacheado publicamente;
- usuários diferentes geram respostas diferentes para a mesma mídia;
- consultas comunitárias bloqueiam dados básicos;
- álbuns e séries tendem a custar mais que filmes e livros;
- o tempo de resposta cresce com o volume de faixas, temporadas e avaliações.

## Arquitetura recomendada

Separar a leitura em três recursos.

## 1. Detalhes públicos

```http
GET /v1/media/{mediaId}
```

Deve retornar somente dados públicos e estáveis.

Campos sugeridos:

- ID;
- título;
- título original;
- descrição;
- tagline;
- tipo;
- data de lançamento;
- idioma e país;
- artwork canônico;
- referências externas;
- gêneros;
- criador principal;
- créditos principais;
- detalhes básicos específicos do tipo.

Não deve depender de:

- `@AuthenticationPrincipal`;
- cookies;
- usuário autenticado;
- preferências pessoais;
- visibilidade entre usuários.

DTO sugerido:

```java
public record PublicMediaDetailsResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String originalTitle,
        String creator,
        String description,
        String tagline,
        String coverUrl,
        String backdropUrl,
        String logoUrl,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        Map<String, String> externalReferences,
        List<GenreResponse> genres,
        List<CreditResponse> credits,
        Object details
) {}
```

## 2. Métricas comunitárias

```http
GET /v1/media/{mediaId}/community
```

Resposta sugerida:

```java
public record MediaCommunityResponse(
        long likeCount,
        Double averageRating,
        List<RatingDistributionBucket> ratingDistribution,
        long listCount,
        long completedCount,
        List<MediaCommunityUserResponse> recentLikers,
        List<MediaCommunityUserResponse> recentCompleters
) {}
```

Esse endpoint pode continuar considerando regras de visibilidade, mas deve ser independente da resposta principal.

Caso os resultados variem conforme o usuário autenticado, separar ainda mais:

```http
GET /v1/media/{mediaId}/community
GET /v1/media/{mediaId}/community/me
```

A primeira resposta deve ser totalmente pública e cacheável.

## 3. Estado pessoal do usuário

```http
GET /v1/media/{mediaId}/me
```

Resposta sugerida:

```java
public record UserMediaStateResponse(
        boolean liked,
        UserMediaStatus status,
        Double rating,
        UUID reviewId,
        List<UUID> listIds,
        String customCoverUrl,
        String customBackdropUrl
) {}
```

Pode incluir conforme necessidade:

- progresso;
- datas de início e conclusão;
- review pessoal;
- listas do usuário;
- artwork personalizado;
- permissões de edição;
- avaliação pessoal de faixas, episódios ou temporadas.

Esse endpoint não deve ser cacheado entre usuários.

## Mudanças propostas

## Prioridade P0 — endpoint público independente do usuário

Alterar o controller atual de:

```java
@GetMapping("/{mediaId}")
public ExternalMediaDetailsResponse findDetails(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID mediaId
) {
    return mediaQueryService.findDetails(
            mediaId,
            user == null ? null : user.id()
    );
}
```

Para algo equivalente a:

```java
@GetMapping("/{mediaId}")
public PublicMediaDetailsResponse findDetails(
        @PathVariable UUID mediaId
) {
    return mediaQueryService.findPublicDetails(mediaId);
}
```

O método público não deve receber `userId`.

## Prioridade P0 — extrair métricas comunitárias

Mover `communityStats(mediaId, userId)` para um serviço dedicado:

```text
MediaCommunityQueryService
```

Exemplo:

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaCommunityQueryService {

    public MediaCommunityResponse find(UUID mediaId) {
        // média, distribuição, contagens e usuários recentes
    }
}
```

Controller:

```java
@GetMapping("/{mediaId}/community")
public MediaCommunityResponse findCommunity(
        @PathVariable UUID mediaId
) {
    return mediaCommunityQueryService.find(mediaId);
}
```

## Prioridade P0 — extrair estado pessoal

Criar serviço dedicado:

```text
UserMediaStateQueryService
```

Controller:

```java
@GetMapping("/{mediaId}/me")
public UserMediaStateResponse findMyState(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID mediaId
) {
    if (user == null) {
        throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Autenticação necessária"
        );
    }

    return userMediaStateQueryService.find(mediaId, user.id());
}
```

## Prioridade P0 — remover personalização do DTO público

No endpoint público:

- usar `media.getCoverUrl()`;
- usar `media.getBackdropUrl()`;
- não chamar `userArtworkResolver.resolve(...)`;
- não incluir `myRating`;
- não incluir progresso pessoal;
- não incluir campos dependentes de `userId`.

## Prioridade P1 — separar detalhes públicos de álbuns

Atualmente o carregamento de um álbum inclui todas as faixas e estatísticas pessoais por faixa.

O endpoint público pode retornar:

- tipo do álbum;
- número de faixas;
- artwork animado;
- lista básica de faixas.

Cada faixa pública deve conter apenas:

- ID;
- título;
- disco;
- posição;
- duração;
- indicador explícito;
- média pública opcional.

Remover `myRating` do DTO público.

Caso a tracklist seja grande, considerar endpoint separado:

```http
GET /v1/media/{albumId}/tracks
```

## Prioridade P1 — separar detalhes públicos de séries

O endpoint principal não deve calcular estatísticas pessoais de todas as temporadas.

Retornar apenas:

- status;
- quantidade de temporadas;
- quantidade de episódios;
- última exibição;
- metadados básicos das temporadas.

Dados pessoais podem ir para:

```http
GET /v1/media/{seriesId}/me/progress
```

Ou permanecer dentro de `/me`.

## Prioridade P1 — cache de detalhes públicos

Adicionar cache ao serviço público:

```java
@Cacheable(cacheNames = "mediaDetails", key = "#mediaId")
public PublicMediaDetailsResponse findPublicDetails(UUID mediaId) {
    // ...
}
```

TTL inicial sugerido:

```text
mediaDetails: 30 minutos a 6 horas
```

O TTL pode ser longo porque alterações editoriais devem invalidar o cache explicitamente.

## Prioridade P1 — cache comunitário curto

```java
@Cacheable(cacheNames = "mediaCommunity", key = "#mediaId")
public MediaCommunityResponse find(UUID mediaId) {
    // ...
}
```

TTL sugerido:

```text
30 segundos a 5 minutos
```

Não usar cache compartilhado se a resposta variar conforme o usuário.

## Prioridade P1 — invalidação após mutações

Invalidar `mediaCommunity` após:

- curtir ou remover curtida;
- avaliar ou remover avaliação;
- concluir ou remover conclusão;
- adicionar ou remover mídia de lista pública;
- excluir review ou entrada que afete métricas.

Exemplo:

```java
@CacheEvict(cacheNames = "mediaCommunity", key = "#mediaId")
public void like(UUID mediaId, UUID userId) {
    // ...
}
```

Invalidar `mediaDetails` após:

- alteração de título ou descrição;
- edição de créditos;
- troca de artwork canônico;
- alteração de gêneros;
- atualização de detalhes do tipo;
- importação ou sincronização externa.

## Prioridade P1 — configuração de cache

Usar Caffeine para instância única ou Redis para ambiente distribuído.

Exemplo com Caffeine:

```java
@Bean
public CaffeineCacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofHours(1))
    );
    return manager;
}
```

Caso cada cache precise de TTL diferente, configurar caches individualmente.

## Prioridade P1 — evitar N+1 e excesso de queries

Medir quantas queries cada tipo de mídia executa.

Revisar especialmente:

- `media.getGenres()`;
- `mediaCreditService.summary(media)`;
- temporadas e episódios;
- tracks e `trackMedia`;
- usuários recentes;
- referências externas.

Possíveis abordagens:

- projections;
- `@EntityGraph`;
- fetch joins específicos;
- consultas em lote;
- DTO projections;
- evitar carregar entidades completas quando apenas três campos são necessários.

## Prioridade P1 — projeções para usuários recentes

Hoje os métodos retornam entidades relacionadas e depois transformam `User`.

Preferir projeções diretas:

```java
public interface CommunityUserProjection {
    UUID getId();
    String getUsername();
    String getAvatarUrl();
}
```

Isso evita carregar objetos completos de curtidas, entradas e usuários.

## Prioridade P2 — consolidar métricas agregadas

As métricas atuais exigem múltiplas consultas por página.

Possíveis evoluções:

- tabela agregada `media_community_stats`;
- atualização transacional após mutações;
- eventos assíncronos;
- materialized view;
- cache Redis com recomputação periódica.

Estrutura possível:

```text
media_id
like_count
rating_count
average_rating
list_count
completed_count
updated_at
```

A distribuição de avaliações pode permanecer em consulta separada ou em uma estrutura agregada.

Não implementar agregação persistida antes de medir se o cache e os índices já resolvem o problema.

## Prioridade P2 — cabeçalhos HTTP

O endpoint público pode retornar cache headers:

```http
Cache-Control: public, max-age=60, s-maxage=3600, stale-while-revalidate=86400
```

Endpoints pessoais devem retornar:

```http
Cache-Control: private, no-store
```

Métricas comunitárias podem usar:

```http
Cache-Control: public, max-age=30, s-maxage=120, stale-while-revalidate=300
```

O cache principal pode continuar no Next.js, mas headers coerentes protegem a arquitetura e facilitam uso futuro de CDN.

## Estrutura sugerida de classes

```text
media/
├── controller/
│   ├── MediaQueryController.java
│   ├── MediaCommunityQueryController.java
│   └── UserMediaStateController.java
├── service/
│   ├── MediaQueryService.java
│   ├── MediaCommunityQueryService.java
│   └── UserMediaStateQueryService.java
└── dto/response/
    ├── PublicMediaDetailsResponse.java
    ├── MediaCommunityResponse.java
    └── UserMediaStateResponse.java
```

## Estratégia de migração

### Fase 1

1. Criar os três novos DTOs.
2. Criar `findPublicDetails(mediaId)` sem `userId`.
3. Criar `/community`.
4. Criar `/me`.
5. Manter temporariamente o método antigo para compatibilidade.

### Fase 2

1. Migrar o frontend para os endpoints novos.
2. Remover campos pessoais do endpoint principal.
3. Remover chamadas comunitárias do caminho crítico.
4. Atualizar testes de contrato.

### Fase 3

1. Adicionar cache.
2. Adicionar invalidação nas mutações.
3. Revisar índices e queries.
4. Medir ganhos por tipo de mídia.
5. Remover o contrato legado.

## Testes necessários

## Testes do endpoint público

- retorna dados sem usuário autenticado;
- retorna a mesma estrutura para usuários diferentes;
- não inclui avaliação pessoal;
- não inclui artwork personalizado;
- não inclui progresso pessoal;
- retorna `404` para mídia inexistente.

## Testes do endpoint comunitário

- retorna contagens públicas corretas;
- respeita entradas privadas;
- não expõe usuários invisíveis;
- retorna distribuição correta;
- invalida cache após mutações.

## Testes do endpoint pessoal

- exige autenticação;
- retorna estado somente do usuário autenticado;
- não compartilha cache;
- retorna avaliação, status, listas e artwork corretos;
- não vaza dados entre usuários.

## Testes de performance

Criar cenários para:

- filme simples;
- livro;
- álbum com muitas faixas;
- série com muitas temporadas;
- mídia com alto volume de avaliações e curtidas.

Medir:

- tempo total;
- número de queries SQL;
- cache hit e miss;
- alocação de memória;
- tamanho da resposta JSON.

## Critérios de aceite

- `GET /v1/media/{id}` não depende do usuário autenticado.
- A resposta pública pode ser compartilhada por cache.
- Métricas comunitárias não bloqueiam os detalhes principais.
- Estado pessoal é carregado por endpoint privado.
- Não há `myRating` ou progresso pessoal no DTO público.
- Artwork personalizado não altera a resposta pública.
- Consultas de álbum e série não calculam dados pessoais de todos os itens no endpoint principal.
- Mutações invalidam os caches correspondentes.
- Falha em métricas comunitárias não impede a leitura dos detalhes públicos.

## Metas iniciais

Em ambiente de produção, após aquecimento:

```text
Detalhes públicos com cache hit: <= 100 ms
Detalhes públicos sem cache: <= 300 ms
Métricas comunitárias: <= 300 ms
Estado pessoal: <= 250 ms
```

As metas devem ser ajustadas conforme infraestrutura, localização do banco e volume de dados.

## Instrumentação

Adicionar timers com Micrometer:

```java
@Timed(value = "cabinet.media.details.public")
public PublicMediaDetailsResponse findPublicDetails(UUID mediaId) {
    // ...
}
```

Métricas recomendadas:

```text
cabinet.media.details.public
cabinet.media.community
cabinet.media.user_state
cabinet.media.details.cache.hit
cabinet.media.details.cache.miss
```

Também acompanhar:

- percentis p50, p95 e p99;
- quantidade de queries por requisição;
- conexões usadas no pool;
- taxa de erro;
- tamanho médio das respostas.

## Resultado esperado

Fluxo final:

```text
GET /v1/media/{id}
        ↓
resposta pública pequena e cacheável

GET /v1/media/{id}/community
        ↓
métricas agregadas com cache curto

GET /v1/media/{id}/me
        ↓
estado privado do usuário sem cache compartilhado
```

A principal melhoria é transformar os detalhes públicos em um recurso estável e barato, deixando consultas comunitárias e personalizadas fora do caminho crítico da página.