# Contexto
Continuando o projeto anterior (Habit Tracker em Java 21/Spring Boot 3), agora precisamos implementar a camada de Autenticação e Segurança.
O requisito mandatório é usar **OAuth2 com Google** para Login e Cadastro. Não haverá tabela de senhas locais.

# Alterações no Domínio
1. **Nova Entidade: User**
    - Campos: `id` (UUID ou Long), `email` (unique), `name`, `avatarUrl`, `googleId` (sub do token), `createdAt`.
2. **Atualização na Entidade: Habit**
    - Adicione um relacionamento `@ManyToOne` com a entidade `User`.
    - Um hábito obrigatoriamente pertence a um usuário.

# Requisitos de Segurança (Spring Security)
1. **Dependências:** Adicione `spring-boot-starter-oauth2-client` e `spring-boot-starter-security` ao `pom.xml`.
2. **Security Configuration:**
    - Configure o `SecurityFilterChain`.
    - Todos os endpoints `/api/**` devem exigir autenticação (`.authenticated()`).
    - Configure o suporte a OAuth2 Login (`.oauth2Login()`).
3. **Persistência Automática (Sync):**
    - Preciso de um `CustomOAuth2UserService` (estendendo `DefaultOAuth2UserService`) ou um `AuthenticationSuccessHandler`.
    - **Comportamento:** Quando o Google retornar o sucesso do login, verifique se o email já existe no banco `users`.
        - Se existir: Atualize o nome/avatar e retorne o usuário.
        - Se não existir: Crie um novo usuário automaticamente no banco e retorne-o.
        - Isso garante que o "Registro" e o "Login" sejam o mesmo fluxo transparente.

# Atualização dos Services/Repositories
1. Refatore o `HabitService` e `HabitRepository`.
2. Todas as buscas (`findAll`, `findById`) devem agora filtrar pelo usuário autenticado. O usuário A não pode ver os hábitos do usuário B.
    - *Dica:* Injete o `Principal` ou `Authentication` nos métodos do Controller e passe o usuário para o Service, ou recupere o usuário logado via `SecurityContextHolder` dentro do Service.

# Entregáveis
1. O novo `pom.xml` atualizado.
2. A classe `User` (Entity).
3. A classe `SecurityConfig`.
4. A lógica de sincronização do usuário (Service ou Handler de OAuth).
5. Exemplo de como fica o `application.properties` (com placeholders para `CLIENT_ID` e `CLIENT_SECRET` do Google).
6. O `HabitController` atualizado recebendo o usuário logado.

Mantenha o código stateless se possível, ou configure a sessão de forma simples para este MVP.