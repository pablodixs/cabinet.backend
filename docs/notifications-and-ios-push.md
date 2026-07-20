# Notificações e push no iOS

Este documento descreve a caixa de notificações existente no Cabinet e o trabalho necessário para entregar as mesmas notificações como push em um aplicativo iOS escrito em Swift.

## Estado atual

O backend já mantém notificações persistidas e autenticadas. O frontend web usa REST como fonte de verdade e SSE apenas como um sinal para atualizar os dados.

| Capacidade | Estado |
| --- | --- |
| Notificações persistidas | Implementada |
| Paginação e contador de não lidas | Implementados |
| Marcação em lote como lida | Implementada |
| Atualização web em tempo real via SSE | Implementada |
| Recuperação por consulta periódica no web | Implementada, a cada 60 segundos |
| Push remoto para iOS via APNs | Não implementado |
| Cadastro de instalações e device tokens | Não implementado |
| Preferências de push por usuário | Não implementadas |
| Fila durável e worker de entrega | Não implementados |

SSE não é push móvel. Ele depende de uma conexão HTTP ativa e, portanto, não atende o caso em que o iOS suspendeu ou encerrou o aplicativo. Para esse caso, o backend precisa atuar como um provider do Apple Push Notification service (APNs).

## Regras de negócio existentes

As ações do próprio destinatário nunca geram uma notificação para ele.

| Tipo | Gatilho | Destinatário | Agrupamento |
| --- | --- | --- | --- |
| `LIST_LIKED` | Curtida em lista pública | Dono da lista | Uma entrada por lista |
| `REVIEW_LIKED` | Curtida em review pública | Autor da review | Uma entrada por review |
| `LIST_COMMENTED` | Comentário ou resposta em lista pública | Dono da lista | Não agrupado |
| `REVIEW_COMMENTED` | Comentário ou resposta em review pública | Autor da review | Não agrupado |
| `COMMENT_REPLIED` | Resposta a comentário | Autor do comentário-pai | Não agrupado |
| `REPORT_RESOLVED` | Reporte sai de `PENDING` | Autor do reporte | Uma entrada por reporte |

Uma resposta pode ter o dono do conteúdo e o autor do comentário-pai como destinatários. Destinatários repetidos são eliminados; se a mesma pessoa ocupar os dois papéis, ela recebe uma única entrada do tipo `COMMENT_REPLIED`.

### Curtidas agrupadas

- `actor` representa a pessoa que curtiu mais recentemente.
- `actorCount` representa o total atual de curtidas de outras pessoas.
- Uma nova curtida atualiza `activityAt` e torna a entrada novamente não lida.
- Ao remover uma curtida, a quantidade e o ator mais recente são recalculados.
- Se não houver mais curtidas externas ativas, a notificação é removida.
- O texto é montado pelo cliente. Exemplo: `Ana e mais 4 pessoas curtiram sua lista “Favoritos”.`

### Comentários e respostas

- Somente listas e reviews públicas aceitam comentários.
- Um comentário pode ter no máximo uma resposta de profundidade; não é permitido responder a uma resposta.
- A exclusão do comentário remove as notificações relacionadas a ele.
- Se um comentário raiz excluído tiver respostas, a thread é preservada com o texto `Comentário removido`.

### Reportes

Uma notificação `REPORT_RESOLVED` é criada uma única vez quando a moderação aprova ou rejeita um reporte ainda pendente. A resposta contém `reportStatus` com `APPROVED` ou `REJECTED` e pode conter `resolutionNote`.

### Retenção e consistência

- Notificações cuja última atividade tenha mais de 90 dias são excluídas diariamente.
- A ordenação usa `activityAt DESC, id DESC`.
- O sinal SSE é publicado somente depois do commit da transação.
- O registro SSE está em memória e pressupõe uma única instância do backend. A persistência REST continua correta mesmo se um sinal em tempo real for perdido.

## Autenticação

As rotas de notificações exigem a sessão `CABINET_SESSION`. O app iOS deve preservar o cookie retornado no login; uma `URLSessionConfiguration.default` com cookies habilitados pode usar `HTTPCookieStorage.shared`.

Requisições mutáveis também exigem CSRF. Antes de um `POST`, `PUT`, `PATCH` ou `DELETE`, consulte:

```http
GET /v1/auth/csrf
```

Exemplo de resposta:

```json
{
  "token": "csrf-token",
  "headerName": "X-XSRF-TOKEN"
}
```

O app deve enviar `token` no cabeçalho indicado por `headerName`, usando a mesma sessão/cookie.

## API de notificações existente

### Listar notificações

```http
GET /v1/me/notifications?page=0&size=20
```

- `page` começa em zero.
- `size` deve estar entre 1 e 50.
- A consulta sempre é isolada pelo usuário da sessão.

Exemplo:

```json
{
  "items": [
    {
      "id": "7e8d7375-6e90-49ca-b417-89fc983938cc",
      "type": "LIST_LIKED",
      "actor": {
        "id": "eb00cc03-20b4-4782-a193-d0550f47b233",
        "username": "ana",
        "displayName": "Ana",
        "avatarUrl": null
      },
      "actorCount": 5,
      "subject": {
        "kind": "LIST",
        "id": "a38d06f8-5768-46b8-8baa-753c893174ec",
        "title": "Favoritos"
      },
      "preview": null,
      "reportStatus": null,
      "resolutionNote": null,
      "occurredAt": "2026-07-19T15:20:30Z",
      "read": false,
      "href": "/listas/a38d06f8-5768-46b8-8baa-753c893174ec#comments"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Campos opcionais podem ser `null`. Em especial, `actor` pode ser `null` se a conta do ator não estiver mais disponível. `preview` contém o comentário relacionado quando aplicável.

### Consultar quantidade não lida

```http
GET /v1/me/notifications/unread-count
```

```json
{
  "unreadCount": 12
}
```

### Marcar as entradas exibidas como lidas

```http
PATCH /v1/me/notifications/read
Content-Type: application/json
X-XSRF-TOKEN: {csrf-token}

{
  "ids": [
    "7e8d7375-6e90-49ca-b417-89fc983938cc"
  ]
}
```

A lista deve conter de 1 a 100 UUIDs. O backend ignora IDs que não pertençam ao usuário da sessão. A resposta de sucesso é `204 No Content`.

### Stream para clientes em primeiro plano

```http
GET /v1/me/notifications/stream
Accept: text/event-stream
```

Eventos emitidos:

```text
event: connected
data: ready

event: notifications-changed
data: refresh

event: heartbeat
data: ping
```

`notifications-changed` não contém a notificação. Ao recebê-lo, o cliente invalida e consulta novamente a lista e o contador. Há heartbeat a cada 25 segundos, timeout de 30 minutos, limite padrão de três streams por usuário e mil streams no processo.

Para um app iOS, o SSE é opcional enquanto o app está visível. A implementação inicial pode consultar REST ao entrar em foreground e usar APNs quando estiver fora dele. Não é necessário manter SSE para concluir a primeira versão móvel.

## Contrato recomendado para navegação móvel

O campo `href` atual representa uma rota web. O app não deve interpretar strings como `/listas/...` para decidir sua navegação. Antes de publicar o cliente móvel, recomenda-se adicionar à resposta REST um destino estruturado e reutilizá-lo no payload APNs:

```json
{
  "destination": {
    "kind": "LIST",
    "id": "a38d06f8-5768-46b8-8baa-753c893174ec",
    "section": "COMMENTS"
  }
}
```

Valores sugeridos:

- `kind`: `LIST`, `REVIEW`, `MEDIA` ou `NOTIFICATIONS`;
- `id`: UUID opcional para o recurso;
- `section`: `COMMENTS` ou `DETAIL`, quando aplicável.

Para reportes vinculados a uma obra, o destino deve ser `MEDIA`; caso contrário, `NOTIFICATIONS`. `subject` continua descrevendo o assunto da notificação e não precisa assumir a função de rota.

## Arquitetura necessária para push iOS

O fluxo recomendado é:

```text
Ação de domínio
  -> grava/atualiza notification e push_outbox na mesma transação
  -> commit
  -> publica o sinal SSE
  -> worker consome push_outbox
  -> busca o estado mais recente da notification
  -> envia para todos os devices ativos do destinatário
  -> APNs entrega ao iOS quando possível
  -> app abre a rota e sincroniza a REST
```

O push é um aviso, não a fonte de verdade. A caixa persistida continua responsável por paginação, estado de leitura, agrupamento e recuperação de eventos perdidos. A entrega APNs é best-effort e não comprova que a pessoa viu o alerta.

### 1. Configurar a conta Apple e o projeto

É necessário:

1. Ter um App ID explícito para o bundle ID do aplicativo.
2. Habilitar Push Notifications nesse App ID e no target do Xcode.
3. Criar uma APNs Authentication Key no Apple Developer e guardar:
   - arquivo privado `.p8`;
   - Key ID;
   - Team ID;
   - bundle ID usado como tópico APNs.
4. Gerar novamente os perfis de provisionamento se a capability tiver sido adicionada depois.
5. Separar os ambientes Development e Production. Builds distribuídos, inclusive TestFlight, usam o ambiente de produção.

O arquivo `.p8` pertence exclusivamente ao servidor. Ele nunca deve entrar no repositório, no bundle do app ou em uma variável compilada no cliente.

Configurações esperadas no deployment, com nomes ajustáveis ao padrão do projeto:

```text
APNS_ENABLED
APNS_TEAM_ID
APNS_KEY_ID
APNS_PRIVATE_KEY
APNS_BUNDLE_ID
APNS_ENVIRONMENT
```

`APNS_PRIVATE_KEY` deve vir de um secret manager ou arquivo montado fora da imagem. O backend deve falhar de forma explícita na inicialização quando push estiver habilitado e alguma configuração obrigatória estiver ausente.

### 2. Persistir instalações e device tokens

Adicionar uma migration Flyway nova; migrations já aplicadas não devem ser editadas. Uma estrutura recomendada é:

```text
push_devices
  id UUID PK
  installation_id UUID NOT NULL
  user_id UUID NOT NULL FK users
  platform VARCHAR NOT NULL                 // IOS
  token_hash VARCHAR NOT NULL               // busca e unicidade
  token_ciphertext TEXT NOT NULL            // token reversível, criptografado em repouso
  apns_environment VARCHAR NOT NULL          // DEVELOPMENT ou PRODUCTION
  bundle_id VARCHAR NOT NULL
  locale VARCHAR NULL
  app_version VARCHAR NULL
  enabled BOOLEAN NOT NULL
  last_registered_at TIMESTAMPTZ NOT NULL
  invalidated_at TIMESTAMPTZ NULL
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL
```

Restrições recomendadas:

- unicidade por `apns_environment + bundle_id + token_hash`;
- unicidade por `bundle_id + installation_id`;
- um usuário pode ter vários devices;
- um token registrado novamente por outro usuário autenticado deve ser transferido para a conta atual;
- `user_id` sempre vem da sessão, nunca do corpo da requisição;
- o ambiente APNs deve ser inferido ou validado pela configuração do deployment, não aceito cegamente do cliente;
- tokens não devem aparecer em logs, métricas ou mensagens de erro.

O token precisa ser recuperável para envio, portanto armazenar somente um hash não é suficiente. Se não houver criptografia de coluna disponível no primeiro release, use o armazenamento seguro do banco e controle de acesso estrito, mantendo um hash separado para consulta e mascarando o valor em logs.

### 3. Expor endpoints de instalação

Contrato sugerido e idempotente:

```http
PUT /v1/me/push-devices/{installationId}
Content-Type: application/json
X-XSRF-TOKEN: {csrf-token}

{
  "platform": "IOS",
  "token": "device-token-em-hexadecimal",
  "locale": "pt-BR",
  "appVersion": "1.0.0"
}
```

Resposta: `204 No Content`.

```http
DELETE /v1/me/push-devices/{installationId}
X-XSRF-TOKEN: {csrf-token}
```

Resposta: `204 No Content`.

O `PUT` deve fazer upsert e reativar a instalação. O `DELETE` deve desabilitar somente a instalação atual. No logout, o app deve desregistrar a instalação antes de invalidar a sessão.

Como evolução, adicionar:

```http
GET /v1/me/notification-preferences
PATCH /v1/me/notification-preferences
```

As preferências podem habilitar/desabilitar push para curtidas, comentários/respostas e moderação sem afetar a caixa persistida.

### 4. Criar uma outbox durável

Não se deve enviar para a Apple dentro da transação HTTP nem usar diretamente todo `NotificationChangedEvent` existente.

O evento atual também ocorre quando o usuário marca entradas como lidas, quando uma curtida é removida e quando uma notificação de comentário é apagada. Essas mudanças precisam atualizar SSE/REST, mas não podem produzir um alerta push.

Adicionar uma `push_outbox`, com pelo menos:

```text
push_outbox
  id UUID PK
  notification_id UUID NOT NULL
  notification_activity_at TIMESTAMPTZ NOT NULL
  status VARCHAR NOT NULL                 // PENDING, PROCESSING, SENT, FAILED, CANCELLED
  attempt_count INTEGER NOT NULL
  available_at TIMESTAMPTZ NOT NULL
  locked_at TIMESTAMPTZ NULL
  processed_at TIMESTAMPTZ NULL
  last_error_code VARCHAR NULL
  created_at TIMESTAMPTZ NOT NULL
```

Regras:

- a outbox é gravada na mesma transação que cria ou atualiza a notificação;
- somente nova curtida, novo comentário/resposta e resolução de reporte enfileiram push;
- unlike, exclusão e marcação como lida não enfileiram push;
- antes de enviar, o worker relê a notificação; se ela não existir mais ou estiver obsoleta, cancela o job;
- jobs devem ser reclamados com lock seguro, por exemplo `FOR UPDATE SKIP LOCKED`, para permitir múltiplos workers;
- tentativas transitórias usam backoff e devem ser idempotentes;
- guardar o `apns-id` retornado e métricas de sucesso/erro facilita suporte.

Para curtidas agrupadas, faça upsert de um job pendente por `notification_id` e renderize o estado mais recente na hora do envio. Um debounce curto, por exemplo 10–30 segundos, evita vários alertas para uma sequência rápida de curtidas. Use também o UUID da notificação como `apns-collapse-id`.

### 5. Implementar o provider APNs no backend

O provider precisa:

- manter conexões HTTP/2 com TLS 1.2 ou superior;
- assinar um JWT ES256 com a chave `.p8`, Key ID e Team ID;
- renovar e reutilizar o provider token conforme as regras da Apple;
- enviar para `api.sandbox.push.apple.com` em Development e `api.push.apple.com` em Production;
- enviar `POST /3/device/{deviceToken}`;
- usar `apns-topic: {bundleId}`;
- usar `apns-push-type: alert`;
- usar prioridade `5` por padrão para notificações sociais; use `10` somente se o produto exigir ação imediata;
- definir `apns-expiration`, por exemplo 24 horas, para evitar alertas sociais muito antigos;
- definir `apns-id` com o identificador do job e `apns-collapse-id` com o identificador da notificação agrupada.

Não abra uma conexão nova por mensagem. Use um cliente APNs maduro compatível com HTTP/2 e mantenha o pool de conexões durante a vida do processo.

O texto visível hoje é construído no frontend web. Para push, crie no backend um `NotificationMessageRenderer`, coberto por testes para todos os tipos e plurais. O renderer deve ler a notificação mais recente no momento do envio, especialmente para curtidas agrupadas.

### 6. Tratar respostas do APNs

- `200`: envio aceito pelo APNs; marque a entrega como enviada.
- `410 Unregistered`, `ExpiredToken`, `BadDeviceToken` ou `DeviceTokenNotForTopic`: desabilite a instalação e não repita com esse token.
- `429`: reagende com backoff e jitter.
- `500`/`503`: reagende conforme a orientação da Apple e registre a indisponibilidade.
- erros de autenticação, tópico, payload ou ambiente: falha de configuração; alerte a operação em vez de repetir indefinidamente.

Um `200` significa que o APNs aceitou a solicitação, não que o alerta foi mostrado ou tocado.

### 7. Payload recomendado

Mantenha o payload pequeno, sem conteúdo sensível e com referências estruturadas:

```json
{
  "aps": {
    "alert": {
      "title": "Cabinet",
      "body": "Ana e mais 4 pessoas curtiram sua lista “Favoritos”."
    },
    "sound": "default",
    "badge": 12,
    "thread-id": "list:a38d06f8-5768-46b8-8baa-753c893174ec",
    "category": "CABINET_NOTIFICATION"
  },
  "notificationId": "7e8d7375-6e90-49ca-b417-89fc983938cc",
  "notificationType": "LIST_LIKED",
  "targetKind": "LIST",
  "targetId": "a38d06f8-5768-46b8-8baa-753c893174ec",
  "targetSection": "COMMENTS"
}
```

O payload comum de remote notification tem limite de 4 KB. Não envie o texto completo de comentários nem notas de moderação na tela bloqueada. O app usa os identificadores para navegar e consulta a API para obter o estado canônico.

`badge` deve ser calculado a partir do contador atual de não lidas. Quando o app for aberto, voltar ao foreground ou marcar notificações como lidas, ele deve consultar `/unread-count` e chamar `UNUserNotificationCenter.current().setBadgeCount(...)`.

## Implementação no app Swift

Os exemplos abaixo assumem SwiftUI e uma versão moderna do iOS.

### 1. Adicionar capabilities

No target do Xcode, em **Signing & Capabilities**:

1. adicionar **Push Notifications**;
2. confirmar que o entitlement `aps-environment` aparece na assinatura;
3. adicionar **Background Modes > Remote notifications** somente se o produto também implementar silent/background push.

Alert push comum não precisa do background mode. Silent push não deve ser usado como única forma de sincronização porque sua execução pode ser atrasada ou omitida pelo sistema.

### 2. Pedir permissão no contexto correto

Peça autorização depois de explicar o benefício, idealmente após o login ou quando a pessoa habilitar notificações nas preferências:

```swift
import UIKit
import UserNotifications

enum PushPermission {
    static func requestAndRegister() async throws -> Bool {
        let center = UNUserNotificationCenter.current()
        let granted = try await center.requestAuthorization(
            options: [.alert, .badge, .sound]
        )

        guard granted else { return false }

        await MainActor.run {
            UIApplication.shared.registerForRemoteNotifications()
        }
        return true
    }
}
```

Não repita o prompt quando a autorização tiver sido negada. Leia `notificationSettings()` e, se necessário, ofereça um atalho para os Ajustes do sistema.

### 3. Receber e registrar o token

Conecte um app delegate ao ciclo de vida SwiftUI:

```swift
import SwiftUI
import UIKit
import UserNotifications

@main
struct CabinetApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()

        Task {
            do {
                try await PushRegistrationService.shared.register(token: token)
            } catch {
                // Registrar sem expor o token e tentar novamente quando houver sessão/rede.
            }
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Telemetria sem dados sensíveis; uma nova tentativa pode ocorrer mais tarde.
    }
}
```

O app deve chamar `registerForRemoteNotifications()` em cada inicialização autorizada e encaminhar ao backend o token atual recebido. O token é opaco e de tamanho variável; não o trunque nem dependa de um tamanho fixo. Não use o token como identidade do usuário ou da instalação.

O `installationId` é um UUID próprio do app, persistido no Keychain. Ele identifica a instalação para o endpoint idempotente, mas não substitui o device token fornecido pelo APNs.

### 4. Registrar usando sessão e CSRF

Esqueleto do serviço de registro:

```swift
struct CsrfResponse: Decodable {
    let token: String
    let headerName: String
}

struct PushDeviceRequest: Encodable {
    let platform = "IOS"
    let token: String
    let locale: String
    let appVersion: String
}

actor PushRegistrationService {
    static let shared = PushRegistrationService()

    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.httpCookieStorage = .shared
        configuration.httpShouldSetCookies = true
        return URLSession(configuration: configuration)
    }()

    func register(token: String) async throws {
        guard SessionStore.shared.isAuthenticated else { return }

        let csrf: CsrfResponse = try await API.shared.get("/v1/auth/csrf")
        let installationId = try InstallationID.current()
        let version = Bundle.main.object(
            forInfoDictionaryKey: "CFBundleShortVersionString"
        ) as? String ?? "unknown"

        var request = URLRequest(
            url: API.baseURL.appending(
                path: "/v1/me/push-devices/\(installationId.uuidString)"
            )
        )
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(csrf.token, forHTTPHeaderField: csrf.headerName)
        request.httpBody = try JSONEncoder().encode(
            PushDeviceRequest(
                token: token,
                locale: Locale.current.identifier,
                appVersion: version
            )
        )

        let (_, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw PushRegistrationError.rejected
        }
    }
}
```

Adapte `API`, `SessionStore`, `InstallationID` e o tratamento de erros à arquitetura do aplicativo. O login, o CSRF e o registro do token precisam usar o mesmo armazenamento de cookies.

Se o callback do token ocorrer antes do login, mantenha somente a intenção de registrar e chame `registerForRemoteNotifications()` novamente após autenticar. A Apple recomenda obter o token atualizado do sistema em vez de usar um token antigo salvo localmente.

### 5. Exibir em foreground e tratar o toque

```swift
extension AppDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        NotificationStore.shared.refresh()
        completionHandler([.banner, .badge, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let payload = response.notification.request.content.userInfo

        Task {
            defer { completionHandler() }

            if let id = payload["notificationId"] as? String {
                try? await NotificationAPI.shared.markRead(ids: [id])
            }

            await NotificationRouter.shared.open(payload: payload)
        }
    }
}
```

O router deve validar os campos, aceitar tipos desconhecidos sem crash e usar uma rota segura como fallback:

| `targetKind` | Destino no app |
| --- | --- |
| `LIST` | Detalhe da lista, seção de comentários |
| `REVIEW` | Detalhe da review, seção de comentários |
| `MEDIA` | Detalhe da obra |
| ausente/desconhecido | Caixa de notificações |

Depois do toque, o app marca somente o UUID recebido como lido, atualiza o contador e consulta os dados do destino. Se o recurso tiver sido removido, exibe uma mensagem não bloqueante e volta para a caixa.

### 6. Sincronizar o ciclo de vida

O app deve:

- depois do login: consultar permissões, registrar no APNs e enviar o token atual;
- ao receber novo token: fazer `PUT` idempotente;
- ao entrar em foreground: consultar notificações e contador;
- ao abrir uma página da caixa: marcar somente as entradas visíveis como lidas;
- ao tocar no push: marcar aquela entrada como lida e navegar;
- no logout: fazer `DELETE` da instalação antes de `POST /v1/auth/logout`;
- ao trocar de conta no mesmo aparelho: transferir o registro para o usuário autenticado atual;
- com permissão negada: continuar oferecendo normalmente a caixa interna.

## Localização e privacidade

Para o primeiro release, o backend pode renderizar título e corpo em `pt-BR`, usando o locale armazenado no device. Se houver suporte a vários idiomas, há duas opções:

- o backend renderiza o texto no locale de cada instalação;
- o payload usa `loc-key` e `loc-args`, com as traduções e plurais mantidos no bundle iOS.

Evite incluir prévias de comentário e notas de resolução no push por padrão. Elas podem aparecer na tela bloqueada. IDs opacos, tipo e destino são suficientes para buscar os detalhes depois da autenticação.

## Observabilidade

Criar métricas e logs para:

- tamanho e idade do backlog da outbox;
- tentativas, latência e taxa de sucesso APNs;
- respostas por status e `reason`;
- tokens desabilitados;
- falhas de autenticação, tópico ou ambiente;
- quantidade de devices ativos por usuário, sem expor tokens.

Alertar para backlog crescente, `403` do APNs, aumento de `BadDeviceToken` e falhas contínuas `5xx`.

## Plano de entrega

### Backend

- [ ] Adicionar `destination` estruturado ao `NotificationResponse`.
- [ ] Criar migration de `push_devices` e `push_outbox` sem alterar migrations antigas.
- [ ] Criar entidade, repositório, serviço e endpoints de instalações.
- [ ] Proteger mutations com sessão, CSRF e isolamento por usuário.
- [ ] Criar evento específico de push nos gatilhos corretos.
- [ ] Gravar outbox na mesma transação da notificação.
- [ ] Implementar worker, lock concorrente, backoff e idempotência.
- [ ] Integrar APNs Development e Production com chave em secret manager.
- [ ] Desabilitar tokens inválidos conforme as respostas do APNs.
- [ ] Adicionar preferências por categoria, se fizerem parte do primeiro release.
- [ ] Testar múltiplos devices, troca de conta e logout.

### App Swift

- [ ] Configurar App ID, assinatura e capability de Push Notifications.
- [ ] Pedir autorização no contexto escolhido pelo produto.
- [ ] Registrar no APNs a cada inicialização autorizada.
- [ ] Persistir `installationId` no Keychain.
- [ ] Enviar o token atual com sessão e CSRF.
- [ ] Tratar falha e repetição idempotente do registro.
- [ ] Implementar caixa paginada e contador pela REST.
- [ ] Marcar somente itens visíveis como lidos.
- [ ] Tratar foreground, toque, deep link e destino removido.
- [ ] Atualizar o badge no launch, foreground e após leitura.
- [ ] Desregistrar a instalação antes do logout.
- [ ] Tratar autorização negada sem bloquear notificações internas.

### Testes de aceite

- [ ] Nova curtida gera caixa + um push; unlike não gera push.
- [ ] Curtidas rápidas são agrupadas e o corpo usa o estado mais recente.
- [ ] Comentário e resposta chegam aos destinatários corretos sem duplicação.
- [ ] Ação do próprio autor não gera caixa nem push.
- [ ] Resolução de reporte gera exatamente um push com a decisão correta.
- [ ] Rollback da transação não deixa job na outbox nem envia push.
- [ ] Token inválido é desativado e deixa de receber tentativas.
- [ ] Retry de falha transitória não duplica a notificação persistida.
- [ ] Um usuário com dois aparelhos recebe nos dois.
- [ ] Logout de um aparelho não desabilita o outro.
- [ ] Troca de conta não envia notificações da conta anterior.
- [ ] Tocar no alerta abre lista, review ou obra correta.
- [ ] O app se recupera por REST quando o push não chega.
- [ ] Development, dispositivo físico e distribuição Production/TestFlight são testados separadamente.

Para testes manuais e inspeção de entrega, use também o [Push Notifications Console](https://developer.apple.com/documentation/usernotifications/testing-notifications-using-the-push-notification-console) da Apple.

## Referências oficiais da Apple

- [Registering your app with APNs](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns)
- [APS Environment Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/aps-environment)
- [Asking permission to use notifications](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications)
- [Setting up a remote notification server](https://developer.apple.com/documentation/usernotifications/setting-up-a-remote-notification-server)
- [Establishing a token-based connection to APNs](https://developer.apple.com/documentation/usernotifications/establishing-a-token-based-connection-to-apns)
- [Sending notification requests to APNs](https://developer.apple.com/documentation/usernotifications/sending-notification-requests-to-apns)
- [Generating a remote notification](https://developer.apple.com/documentation/usernotifications/generating-a-remote-notification)
- [Handling notification responses from APNs](https://developer.apple.com/documentation/usernotifications/handling-notification-responses-from-apns)
- [Handling notifications and notification-related actions](https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions)
