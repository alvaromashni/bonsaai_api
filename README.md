# Bonsaai API

API REST para uma aplicacao de rastreamento de habitos e metas com desafios sociais e monetizacao por assinatura. Desenvolvida em Java com Spring Boot e projetada para deploy em producao.

## Visao Geral

Bonsaai e um sistema backend que permite aos usuarios criar habitos, definir metas e competir com outros usuarios atraves de desafios. O sistema adota um modelo freemium com autenticacao via Google OAuth2, integracao com pagamentos PIX, rate limiting distribuido e analytics de habitos.

## Stack Tecnologica

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 |
| Banco de Dados | PostgreSQL |
| Migracoes | Flyway |
| Autenticacao | OAuth2 (Google) |
| Cache / Rate Limiting | Redis + Bucket4j |
| Pagamentos | Woovi (OpenPix / PIX) |
| Documentacao | Springdoc OpenAPI (Swagger) |
| Build | Maven |
| Deploy | Docker / Render |

## Funcionalidades

### Gerenciamento de Habitos
- Criacao de habitos com frequencia diaria ou semanal e dias-alvo personalizados
- Registro de conclusoes (check-in) por habito
- Arquivamento de habitos sem perda de historico (soft delete)

### Acompanhamento de Metas
- Criacao de metas com prazos e vinculacao de multiplos habitos
- Acompanhamento de progresso por checkpoints
- Marcacao de metas como concluidas
- Plano FREE: 1 meta ativa; Plano PRO: metas ilimitadas

### Desafios Sociais
- Criacao e participacao em desafios por codigo de convite
- Check-in de habitos no contexto do desafio
- Placar com ranking por desafio
- Criacao de desafios restrita a usuarios PRO

### Assinatura e Pagamentos
- Tres planos de assinatura: Mensal (R$ 9,90), Trimestral (R$ 25,90), Anual (R$ 99,90)
- Processamento de pagamentos via PIX pela gateway Woovi/OpenPix
- Processamento idempotente de webhooks para evitar ativacoes duplicadas
- Controle automatico de expiracao de plano

### Rate Limiting
- Algoritmo token bucket via Bucket4j com backend em Redis
- Limites por camada: nao autenticado (50 req/h), FREE (100 req/h), PRO (1000 req/h)

### Analytics
- Insights de desempenho e estatisticas de conclusao de habitos
- Restrito a usuarios PRO

## Estrutura do Projeto

```
src/main/java/dev/mashni/habitsapi/
├── auth/             # Configuracao de seguranca, OAuth2, handler de login
├── habit/            # CRUD de habitos, check-in, arquivamento, logs
├── goal/             # Metas, habitos vinculados, checkpoints
├── challenge/        # Criacao, participacao, check-in e placar de desafios
├── payment/          # Checkout, cliente Woovi, processador de webhooks
├── user/             # Perfil do usuario, gerenciamento de plano
├── analytics/        # Analytics de habitos (somente PRO)
├── ratelimit/        # Interceptor de rate limiting com Bucket4j + Redis
└── shared/           # Configuracao de CORS, handler global de excecoes
```

As migracoes do banco de dados sao versionadas em `src/main/resources/db/migration/` com Flyway.

## Endpoints da API

| Modulo | Metodo | Rota | Descricao |
|---|---|---|---|
| Habitos | POST | /api/habits | Criar habito |
| Habitos | GET | /api/habits | Listar habitos ativos |
| Habitos | GET | /api/habits/archived | Listar habitos arquivados |
| Habitos | GET | /api/habits/{id} | Detalhes do habito |
| Habitos | PUT | /api/habits/{id} | Atualizar habito |
| Habitos | DELETE | /api/habits/{id} | Excluir habito |
| Habitos | PATCH | /api/habits/{id}/archive | Arquivar habito |
| Habitos | POST | /api/habits/{id}/check | Registrar conclusao |
| Metas | POST | /api/goals | Criar meta |
| Metas | GET | /api/goals | Listar metas |
| Metas | GET | /api/goals/{id} | Detalhes da meta |
| Metas | PUT | /api/goals/{id}/habits | Atualizar habitos vinculados |
| Metas | PATCH | /api/goals/{id}/complete | Marcar meta como concluida |
| Metas | DELETE | /api/goals/{id} | Excluir meta |
| Desafios | POST | /api/challenges | Criar desafio (PRO) |
| Desafios | POST | /api/challenges/join | Entrar por codigo de convite |
| Desafios | GET | /api/challenges | Listar desafios do usuario |
| Desafios | GET | /api/challenges/{id} | Detalhes do desafio e placar |
| Desafios | POST | /api/challenges/{id}/check-in | Alternar check-in de habito |
| Pagamentos | POST | /api/payments/checkout | Criar pagamento PIX |
| Pagamentos | GET | /api/payments/{id}/status | Verificar status do pagamento |
| Usuario | GET | /api/me | Dados do usuario autenticado |
| Analytics | GET | /api/analytics | Analytics de habitos (PRO) |
| Webhooks | POST | /api/webhooks/** | Webhook de pagamento Woovi |

A documentacao interativa completa esta disponivel em `/swagger-ui.html` com a aplicacao em execucao.

## Autenticacao

A autenticacao e feita via Google OAuth2. Todos os endpoints `/api/**` exigem sessao autenticada, com excecao do endpoint de webhook de pagamento. Os dados de sessao sao gerenciados por cookies (`SameSite=None`, `Secure=true`) e a protecao CSRF e aplicada via header `X-XSRF-TOKEN`.

## Executando Localmente

### Pre-requisitos
- Java 21
- Docker (para PostgreSQL e Redis)

### Configuracao

1. Clone o repositorio e copie o arquivo de variaveis de ambiente:
   ```bash
   cp .env.example .env
   ```

2. Preencha as variaveis obrigatorias no `.env`:
   ```
   DB_URL=jdbc:postgresql://localhost:5432/bonsaai
   DB_USERNAME=postgres
   DB_PASSWORD=sua_senha
   GOOGLE_CLIENT_ID=seu_google_client_id
   GOOGLE_CLIENT_SECRET=seu_google_client_secret
   FRONTEND_URL=http://localhost:3000
   REDIS_URL=redis://localhost:6379
   WOOVI_APP_ID=seu_woovi_app_id
   WOOVI_API_URL=https://api.openpix.com.br
   WOOVI_WEBHOOK_SECRET=seu_webhook_secret
   ```

3. Suba os servicos de infraestrutura:
   ```bash
   ./start-dev.sh
   ```

4. Execute a aplicacao:
   ```bash
   ./mvnw spring-boot:run
   ```

A API estara disponivel em `http://localhost:8080`.

## Executando com Docker

Um `Dockerfile` multi-stage esta incluso para builds de producao:

```bash
docker build -t bonsaai-api .
docker run -p 8080:8080 --env-file .env bonsaai-api
```

## Executando os Testes

```bash
./mvnw test
```

Os testes utilizam banco de dados H2 em memoria e nao dependem de servicos externos.

## Notas de Arquitetura

- **Soft deletes**: Habitos sao arquivados via anotacoes do Hibernate em vez de excluidos fisicamente, preservando o historico de logs.
- **Webhooks idempotentes**: Eventos de webhook de pagamento sao deduplicados com a tabela `processed_webhook_events`, evitando ativacoes duplas em caso de retentativa.
- **Aplicacao de planos**: As regras de negocio para os planos FREE e PRO sao aplicadas na camada de servico, nao apenas no controller.
- **Rate limiting**: Aplicado via `HandlerInterceptor` do Spring antes de os requests chegarem aos controllers, com buckets por usuario armazenados no Redis.
