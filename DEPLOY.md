# Guia de Deploy - Habits API

Este guia contém instruções para fazer o deploy da Habits API na plataforma Render.

## 📋 Pré-requisitos

Antes de fazer o deploy, você precisa:

1. **Conta no Render**: Crie uma conta em [render.com](https://render.com)
2. **Banco de Dados PostgreSQL**: Configure um banco PostgreSQL (pode ser no próprio Render, Railway, Supabase, etc.)
3. **Credenciais OAuth2 do Google**: Configure em [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
4. **Frontend Deployado**: URL do seu frontend (ex: Vercel, Netlify)

## 🚀 Deploy no Render

### 1. Criar Web Service

1. Acesse o [Dashboard do Render](https://dashboard.render.com)
2. Clique em **"New +"** → **"Web Service"**
3. Conecte seu repositório GitHub/GitLab

### 2. Configurar o Service

**Build & Deploy:**
- **Name**: `habits-api` (ou nome de sua escolha)
- **Region**: Escolha a região mais próxima
- **Branch**: `main`
- **Runtime**: `Docker`
- **Dockerfile Path**: `Dockerfile` (deixar padrão)
- **Docker Build Context**: `.` (deixar padrão)

**Instance:**
- **Instance Type**: Free ou Starter (conforme sua necessidade)

### 3. Configurar Variáveis de Ambiente

Na seção **Environment Variables**, adicione as seguintes variáveis:

#### Obrigatórias:

```bash
# Database Configuration
DB_URL=jdbc:postgresql://seu-host:5432/seu-database
DB_USERNAME=seu_usuario
DB_PASSWORD=sua_senha_segura

# OAuth2 Google
GOOGLE_CLIENT_ID=seu_google_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=seu_google_client_secret

# Frontend URL
FRONTEND_URL=https://seu-frontend.vercel.app
```

#### Opcionais:

```bash
# Habilitar logs SQL (apenas para debug)
SHOW_SQL=false
```

### 4. Configurar OAuth2 Redirect URI

No Google Cloud Console, adicione a URL de callback do Render:

```
https://seu-servico.onrender.com/login/oauth2/code/google
```

### 5. Deploy

1. Clique em **"Create Web Service"**
2. Aguarde o build e deploy (pode levar alguns minutos)
3. Acesse a URL fornecida: `https://seu-servico.onrender.com`

## ✅ Verificar Deploy

Após o deploy, teste os seguintes endpoints:

### Health Check
```bash
curl https://seu-servico.onrender.com/actuator/health
```

Resposta esperada:
```json
{
  "status": "UP"
}
```

### Swagger UI
Acesse no navegador:
```
https://seu-servico.onrender.com/swagger-ui.html
```

### API Documentation
```
https://seu-servico.onrender.com/v3/api-docs
```

## 🗄️ Configurar PostgreSQL no Render

Se você ainda não tem um banco de dados:

1. No Render Dashboard, clique em **"New +"** → **"PostgreSQL"**
2. Configure:
   - **Name**: `habits-db`
   - **Database**: `habitsdb`
   - **User**: `habitsuser`
   - **Region**: Mesma região do Web Service
3. Após criado, copie a **Internal Database URL** ou **External Database URL**
4. Use essa URL na variável de ambiente `DB_URL` do Web Service

**Formato da URL:**
```
jdbc:postgresql://[host]:[port]/[database]
```

Exemplo:
```
jdbc:postgresql://dpg-abc123xyz.render.com:5432/habitsdb_abc
```

## 🔧 Troubleshooting

### Erro: "Failed to connect to database"

**Solução:**
- Verifique se a `DB_URL` está no formato JDBC correto
- Confirme que o banco está acessível
- Verifique credenciais (`DB_USERNAME` e `DB_PASSWORD`)

### Erro: "OAuth2 authentication failed"

**Solução:**
- Verifique se `GOOGLE_CLIENT_ID` e `GOOGLE_CLIENT_SECRET` estão corretos
- Confirme que a Redirect URI está configurada no Google Cloud Console
- Use a URL completa: `https://seu-servico.onrender.com/login/oauth2/code/google`

### Erro: "CORS policy"

**Solução:**
- Verifique se `FRONTEND_URL` está correto e sem barra final
- Confirme que o frontend está usando a URL correta do backend

### Build lento ou timeout

**Solução:**
- O primeiro build pode levar 5-10 minutos
- Builds subsequentes são mais rápidos devido ao cache do Docker
- Se continuar lento, considere usar uma instância paga

## 📊 Monitoramento

### Logs

Acesse os logs em tempo real no Render Dashboard:
- **Logs** tab no seu Web Service
- Visualize erros, requisições e health checks

### Métricas

O Render fornece métricas básicas:
- CPU usage
- Memory usage
- Request count
- Response time

### Health Check Automático

O Dockerfile já inclui health check:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s
```

O Render usará `/actuator/health` para verificar se a aplicação está saudável.

## 🔄 Atualizar Aplicação

Para fazer update após mudanças no código:

1. **Push para o branch main**:
```bash
git add .
git commit -m "Update: sua mensagem"
git push origin main
```

2. **Deploy automático**: O Render detecta automaticamente e faz rebuild

3. **Deploy manual**: Ou clique em "Manual Deploy" no Dashboard

## 💰 Custos

### Render Free Tier
- ✅ Web Service gratuito (com limitações)
- ✅ PostgreSQL gratuito (90 dias, depois $7/mês)
- ⚠️ Serviço gratuito "dorme" após 15 minutos de inatividade
- ⚠️ Primeiro acesso após dormir pode levar 30-60 segundos

### Render Starter ($7/mês)
- ✅ Sem sleep/inatividade
- ✅ Melhor performance
- ✅ Maior confiabilidade

## 🌐 URLs de Produção

Após o deploy, você terá:

- **API**: `https://seu-servico.onrender.com`
- **Health**: `https://seu-servico.onrender.com/actuator/health`
- **Swagger**: `https://seu-servico.onrender.com/swagger-ui.html`
- **Docs API**: `https://seu-servico.onrender.com/v3/api-docs`

## 📝 Checklist Final

Antes de considerar o deploy concluído:

- [ ] Health check retorna `{"status":"UP"}`
- [ ] Swagger UI carrega corretamente
- [ ] Login OAuth2 com Google funciona
- [ ] Frontend consegue se comunicar com o backend
- [ ] CORS está funcionando (requests do frontend)
- [ ] Banco de dados está persistindo dados
- [ ] Variáveis de ambiente estão todas configuradas
- [ ] Redirect URI do OAuth2 está correto

## 🆘 Suporte

Se encontrar problemas:

1. **Logs do Render**: Primeiro lugar para investigar
2. **Health Check**: Verifique se está respondendo
3. **Variáveis de ambiente**: Confirme que estão todas configuradas
4. **Database**: Teste conexão separadamente

---

**Stack de Produção**:
- ☕ Java 21 + Spring Boot
- 🐘 PostgreSQL
- 🐳 Docker (Multi-stage build)
- ☁️ Render Platform
- 🔐 OAuth2 (Google)
- 📊 Spring Actuator (Monitoring)
- 📖 Swagger/OpenAPI (Documentation)

✨ **Deploy concluído com sucesso!** Sua API está rodando em produção.
