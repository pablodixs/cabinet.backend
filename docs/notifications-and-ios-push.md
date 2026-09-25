# Notificações e push no iOS

O Cabinet mantém a caixa de notificações no PostgreSQL. A Web continua buscando pela API e recebe sinais de atualização por SSE. A infraestrutura de entrega FCM está implementada no backend, mas a ativação e o recebimento de push no app iOS estão desativados até que o projeto possa usar APNs/FCM. O payload futuro não incluirá previews nem conteúdo privado.

## Eventos enviados

Push é gerado para tipos persistidos existentes: likes em listas e reviews, comentários e respostas, resoluções de reporte, episódios acompanhados e importações do Letterboxd. Também são persistidos avisos para novos seguidores e para mídia lançada que constava como `PLANNED`. Atualização de coleção e push Web não fazem parte desta entrega.

A entrega é criada na mesma transação que grava a notificação. Cada linha de `notification_deliveries` identifica a notificação e a instalação, com unicidade para tornar a criação idempotente. O worker reivindica entregas no PostgreSQL, envia pelo `PushGateway`/Firebase Admin SDK, registra sucesso, aplica retries com backoff e encerra entregas após oito tentativas. Tokens `UNREGISTERED` são desativados. Preferências são verificadas na criação e novamente antes do envio.

## API

Todas as rotas abaixo exigem a sessão `CABINET_SESSION`; mutações exigem o token CSRF normal do Cabinet.

- `GET /v1/me/notifications/{id}` retorna a notificação somente se pertencer ao usuário autenticado.
- `POST /v1/me/notifications/installations` com `{ "token": "..." }` registra ou atualiza a instalação iOS.
- `DELETE /v1/me/notifications/installations` com o mesmo corpo desativa a instalação daquele usuário.
- `GET /v1/me/notifications/preferences` lista preferências por tipo. Tipos sem configuração gravada começam habilitados.
- `PUT /v1/me/notifications/preferences` com `{ "preferences": [{ "type": "LIST_LIKED", "enabled": false }] }` atualiza os tipos enviados.
- `PUT /v1/me/notifications/local-release-reminders/{mediaId}` registra que existe lembrete local para aquela mídia; `DELETE` remove essa supressão.

Instalações são identificadas pelo token FCM. Se um token mudar de conta, entregas pendentes do proprietário anterior são encerradas antes da transferência. As preferências disponíveis incluem os tipos de notificação atuais, `FOLLOWED` e `MEDIA_RELEASED`.

## FCM e configuração

Para ativar envio no backend, configure `FIREBASE_MESSAGING_ENABLED=true`, `FIREBASE_PROJECT_ID` e credenciais do Firebase Admin via Application Default Credentials ou `GOOGLE_APPLICATION_CREDENTIALS`. Com o recurso desativado (padrão), entregas permanecem persistidas e aguardam configuração, sem chamadas externas.

Para preparar o app iOS, resolver o pacote Swift `firebase-ios-sdk`, adicionar o `GoogleService-Info.plist` do app `br.com.scriptles.cabinet` ao bundle e configurar a chave APNs no projeto Firebase. O plist contém identificadores públicos do app; não adicionar credenciais de serviço ao repositório. A assinatura precisa da capacidade Push Notifications para o ambiente correspondente. Embora dependência e capacidade estejam preparadas no projeto, o app ainda não solicita permissão, registra tokens ou trata abertura de push.

As rotas de instalação e preferências estão prontas para a futura integração iOS. O FCM permanece desativado por padrão; sem instalações registradas o worker não envia notificações. Lembretes locais explícitos de lançamento continuam no iOS. Quando a integração for ativada, o app deverá sincronizar as mídias com lembrete local para evitar push duplicado.

## Destinos e recuperação

O payload remoto futuro conterá somente `notificationId` e `type` em dados, junto com texto genérico de alerta. A futura integração iOS deverá consultar a rota autenticada acima no toque e abrir a atividade se o recurso não estiver disponível. SSE segue apenas como sinal de atualização para clientes Web conectados.

## Operação e verificação

O job `MEDIA_RELEASE_NOTIFICATIONS` usa o cron `MEDIA_RELEASE_NOTIFICATIONS_CRON` (padrão 08:05, horário de São Paulo). A entrega push consulta a fila a cada 1,5 segundo por padrão (`FIREBASE_MESSAGING_POLL_DELAY`). Erros ficam na tabela de entregas sem token FCM em logs.

Validar migrations PostgreSQL, isolamento por usuário, CSRF, instalação/token inválido, preferências, deduplicação, retries, preferência alterada com item na fila, reinício e disputa entre workers. Testes em dispositivo iOS ficam para quando APNs/FCM puder ser ativado. Confirmar também o fluxo SSE Web existente.
