# Análise de Falhas nos Testes

**Data**: 2026-01-13
**Status**: ✅ RESOLVIDO
**Total de Testes**: 46
**Testes com Falha**: 0
**Taxa de Sucesso**: 100%

---

## ✅ Status Final

Todas as falhas foram corrigidas! Os testes agora passam 100%.

---

## 📊 Resumo Executivo

O projeto possui 5 testes falhando, divididos em 2 categorias principais:

1. **3 falhas em HabitControllerIntegrationTest** - Problema de paginação
2. **2 falhas em HabitServiceTest** - Problema de cálculo de streak para SPECIFIC_DAYS

---

## 🔴 Categoria 1: Testes de Integração do Controller (Paginação)

### Testes Falhando

1. `HabitControllerIntegrationTest.testAccessWithAuth_ReturnsOk` (linha 81)
2. `HabitControllerIntegrationTest.testDataIsolation_UserBCannotSeeUserAHabits` (linha 134)
3. `HabitControllerIntegrationTest.testMultipleUsers_IndependentHabits` (linha 253)

### Causa Raiz

O endpoint `GET /api/habits` retorna um objeto de **paginação** do Spring Data:

```json
{
  "content": [],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {...}
  },
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "numberOfElements": 0,
  "size": 20,
  "number": 0,
  "sort": {...},
  "empty": true
}
```

Mas os testes esperam um **array simples**:

```json
[]
```

### Evidência no Código

**HabitController.java (linha 67)**:
```java
@GetMapping
public ResponseEntity<Page<HabitSummaryResponse>> getAllActiveHabits(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    var user = userService.getUserFromAuthentication(authentication);
    Pageable pageable = PageRequest.of(page, size);
    var habits = habitService.getAllActiveHabits(user, pageable);
    return ResponseEntity.ok(habits); // ← Retorna Page<>
}
```

**HabitControllerIntegrationTest.java (linha 81)**:
```java
mockMvc.perform(get("/api/habits")
        .with(oauth2Login().oauth2User(createOAuth2User(userA))))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$").isArray()); // ← Espera array, recebe objeto
```

### Mensagens de Erro

```
Expected: an instance of java.util.List
but: <{content=[], pageable={...}}> is a java.util.LinkedHashMap
```

### Solução

**Opção 1 (Recomendada): Atualizar os Testes**

Modificar os testes para esperar o objeto de paginação:

```java
// Antes
.andExpect(jsonPath("$").isArray())
.andExpect(jsonPath("$.length()").value(1))
.andExpect(jsonPath("$[0].name").value("Habit A"))

// Depois
.andExpect(jsonPath("$.content").isArray())
.andExpect(jsonPath("$.content.length()").value(1))
.andExpect(jsonPath("$.content[0].name").value("Habit A"))
```

**Opção 2: Modificar o Controller**

Retornar apenas o conteúdo da página (sem metadados de paginação):

```java
@GetMapping
public ResponseEntity<List<HabitSummaryResponse>> getAllActiveHabits(...) {
    ...
    return ResponseEntity.ok(habits.getContent()); // ← Retorna apenas o array
}
```

⚠️ **Recomendação**: Manter a paginação (Opção 1) pois é uma prática melhor para APIs RESTful.

---

## 🔴 Categoria 2: Testes de Cálculo de Streak (Lógica de Negócio)

### Testes Falhando

1. `HabitServiceTest.testCalculateStreak_GymRat_SpecificDaysWithGapForgiveness` (linha 307)
   - **Esperado**: streak = 3
   - **Recebido**: streak = 0

2. `HabitServiceTest.testCalculateStreak_SpecificDays_BreaksWhenRequiredDayMissed` (linha 352)
   - **Esperado**: streak = 1
   - **Recebido**: streak = 0

### Causa Raiz

O método `HabitService.calculateCurrentStreak()` possui uma lógica falha para hábitos com `FrequencyType.SPECIFIC_DAYS`.

**Problema Específico**: A verificação inicial (linhas 189-195) retorna `0` prematuramente quando:
1. Não há check-in hoje
2. Não há check-in ontem
3. Ontem OU hoje são dias obrigatórios (targetDays)

#### Exemplo do Bug

**Cenário de Teste**:
- Hábito: Gym (SPECIFIC_DAYS: Mon, Wed, Fri)
- Check-ins: Sexta passada, Quarta passada, Segunda passada
- Hoje: Quinta-feira
- Ontem: Quarta-feira

**Fluxo de Execução Bugado**:

```java
// HabitService.java (linha 186-195)
var today = LocalDate.now();  // Quinta
var startDate = completedDates.contains(today) ? today : today.minusDays(1);
// startDate = Quarta (ontem)

if (!completedDates.contains(startDate) && !completedDates.contains(today)) {
    // ✓ Quarta (ontem) NÃO está em completedDates
    // ✓ Quinta (hoje) NÃO está em completedDates

    if (isRequiredDay(today, habit) || isRequiredDay(today.minusDays(1), habit)) {
        // Quinta é dia obrigatório? NÃO (target: Mon, Wed, Fri)
        // Quarta é dia obrigatório? SIM ✓
        return 0; // ← RETORNA 0 AQUI (ERRO!)
    }
}
```

**Por que está errado?**

A Quarta verificada (ontem) não é a mesma Quarta que teve check-in (8 dias atrás). O método está verificando se ONTEM foi um dia obrigatório, mas não considera que:

1. O último check-in pode ser de vários dias atrás
2. Dias não obrigatórios entre o último check-in e hoje não deveriam zerar o streak (Gap Forgiveness)
3. Deve encontrar o **último dia obrigatório que ocorreu** e verificar se foi completado

### Evidência no Código

**HabitService.java (linha 176-219)**:

```java
private int calculateCurrentStreak(List<HabitLog> logs, Habit habit) {
    if (logs.isEmpty()) {
        return 0;
    }

    var today = LocalDate.now();
    var completedDates = logs.stream()
        .map(HabitLog::getCompletedDate)
        .collect(Collectors.toSet());

    // Start counting from today or yesterday
    var startDate = completedDates.contains(today) ? today : today.minusDays(1);

    // ⚠️ PROBLEMA AQUI: Verificação muito restritiva
    if (!completedDates.contains(startDate) && !completedDates.contains(today)) {
        // Check if today or yesterday were required days
        if (isRequiredDay(today, habit) || isRequiredDay(today.minusDays(1), habit)) {
            return 0; // ← Retorna 0 prematuramente
        }
    }

    int streak = 0;
    var currentDate = startDate;

    // Count backwards while checking required days
    while (!currentDate.isBefore(habit.getStartDate())) {
        boolean isRequired = isRequiredDay(currentDate, habit);
        boolean isCompleted = completedDates.contains(currentDate);

        if (isRequired) {
            if (isCompleted) {
                streak++;
            } else {
                break; // Required day not completed - streak breaks
            }
        }
        // Gap Forgiveness: non-required days don't break streak

        currentDate = currentDate.minusDays(1);
    }

    return streak;
}
```

### Solução

Refatorar o método `calculateCurrentStreak()` para:

1. **Encontrar o último dia obrigatório que ocorreu** (não necessariamente ontem)
2. **Verificar se esse dia foi completado**
3. **Se sim**: começar a contar o streak a partir dele
4. **Se não**: retornar 0

#### Pseudocódigo da Solução

```java
private int calculateCurrentStreak(List<HabitLog> logs, Habit habit) {
    if (logs.isEmpty()) {
        return 0;
    }

    var today = LocalDate.now();
    var completedDates = logs.stream()
        .map(HabitLog::getCompletedDate)
        .collect(Collectors.toSet());

    // NOVO: Encontrar o último dia obrigatório que deveria ter sido completado
    LocalDate lastRequiredDay = findLastRequiredDay(today, habit);

    // NOVO: Verificar se o último dia obrigatório foi completado
    if (!completedDates.contains(lastRequiredDay)) {
        return 0; // Streak quebrado
    }

    // NOVO: Começar a contar a partir do último dia obrigatório completado
    int streak = 0;
    var currentDate = lastRequiredDay;

    while (!currentDate.isBefore(habit.getStartDate())) {
        boolean isRequired = isRequiredDay(currentDate, habit);
        boolean isCompleted = completedDates.contains(currentDate);

        if (isRequired) {
            if (isCompleted) {
                streak++;
            } else {
                break;
            }
        }

        currentDate = currentDate.minusDays(1);
    }

    return streak;
}

// NOVO: Método auxiliar para encontrar o último dia obrigatório
private LocalDate findLastRequiredDay(LocalDate from, Habit habit) {
    LocalDate currentDate = from;

    // Para DAILY, qualquer dia é obrigatório
    if (habit.getFrequencyType() == FrequencyType.DAILY) {
        return currentDate;
    }

    // Para SPECIFIC_DAYS, encontrar o dia obrigatório mais recente
    while (!currentDate.isBefore(habit.getStartDate())) {
        if (isRequiredDay(currentDate, habit)) {
            return currentDate;
        }
        currentDate = currentDate.minusDays(1);
    }

    return habit.getStartDate();
}
```

---

## 🔧 Plano de Correção

### Prioridade 1: Corrigir Testes de Paginação (15-30 min)

1. Atualizar 3 testes em `HabitControllerIntegrationTest.java`
2. Modificar assertions de `$` para `$.content`
3. Rodar testes: `./mvnw test -Dtest=HabitControllerIntegrationTest`
4. Commit: `test: fix pagination assertions in HabitController tests`

### Prioridade 2: Corrigir Lógica de Streak (1-2 horas)

1. Criar novo método `findLastRequiredDay()` em `HabitService`
2. Refatorar `calculateCurrentStreak()` para usar a nova lógica
3. Adicionar testes unitários para o novo método
4. Rodar testes: `./mvnw test -Dtest=HabitServiceTest`
5. Testar manualmente com diferentes cenários
6. Commit: `fix: correct streak calculation for SPECIFIC_DAYS habits`

### Validação Final

```bash
# Rodar TODOS os testes
./mvnw clean test

# Esperado: 46/46 testes passando
```

---

## 📝 Lições Aprendidas

### Sobre Paginação

- ✅ Usar `Page<>` é uma boa prática para APIs RESTful
- ✅ Testes devem refletir a estrutura real da API
- ⚠️ Mudanças de estrutura de resposta devem atualizar testes

### Sobre Cálculo de Streak

- ✅ Lógica de negócio complexa precisa de testes abrangentes
- ✅ Verificações iniciais (early returns) precisam ser cuidadosas
- ⚠️ Gap Forgiveness requer lógica especial para encontrar o "ponto de partida" correto
- ⚠️ Considerar todos os cenários: dias obrigatórios no passado, hoje, futuro

---

## 🚀 Próximos Passos

- [ ] Corrigir testes de paginação
- [ ] Corrigir lógica de streak para SPECIFIC_DAYS
- [ ] Adicionar mais testes de borda (edge cases)
- [ ] Documentar comportamento de Gap Forgiveness na API doc
- [ ] Considerar adicionar testes parametrizados para diferentes dias da semana

---

**Documento criado por**: Claude (AI Assistant)
**Status**: Análise Completa

---

## ✅ Soluções Implementadas

### Correção 1: Testes de Paginação (Commit: 9cf3f04)

**Arquivo**: `HabitControllerIntegrationTest.java`

**Mudanças**:
```diff
- .andExpect(jsonPath("$").isArray())
+ .andExpect(jsonPath("$.content").isArray())

- .andExpect(jsonPath("$.length()").value(1))
+ .andExpect(jsonPath("$.content.length()").value(1))

- .andExpect(jsonPath("$[0].name").value("Habit A"))
+ .andExpect(jsonPath("$.content[0].name").value("Habit A"))
```

**Resultado**: 3 testes corrigidos
- ✅ `testAccessWithAuth_ReturnsOk`
- ✅ `testDataIsolation_UserBCannotSeeUserAHabits`
- ✅ `testMultipleUsers_IndependentHabits`

---

### Correção 2: Lógica de Streak (Commit: 1fc8ab9)

**Arquivo**: `HabitService.java`

**Mudanças**:

1. **Novo método `findLastRequiredDay()`**:
   - Encontra o último dia obrigatório (hoje ou no passado)
   - Para DAILY: retorna hoje
   - Para SPECIFIC_DAYS: retroage até encontrar um targetDay

2. **Refatoração de `calculateCurrentStreak()`**:
   - Verifica se último dia obrigatório foi completado
   - Se não: verifica dia obrigatório anterior (1-day grace)
   - Se sim: começa a contar streak a partir dele
   - Se ambos não completados: streak = 0

**Código-chave**:
```java
// Find the most recent required day
LocalDate lastRequiredDay = findLastRequiredDay(today, habit);

// Determine starting point with 1-day grace
if (completedDates.contains(lastRequiredDay)) {
    streakStartDate = lastRequiredDay;
} else {
    // Check previous required day (grace period)
    LocalDate previousRequiredDay = findLastRequiredDay(lastRequiredDay.minusDays(1), habit);
    if (completedDates.contains(previousRequiredDay)) {
        streakStartDate = previousRequiredDay;
    } else {
        return 0; // No streak
    }
}
```

**Resultado**: 3 testes corrigidos (incluindo 1 novo que apareceu após primeira tentativa)
- ✅ `testCalculateStreak_GymRat_SpecificDaysWithGapForgiveness`
- ✅ `testCalculateStreak_SpecificDays_BreaksWhenRequiredDayMissed`
- ✅ `testCalculateStreak_YesterdayOnly_ReturnsOne`

---

## 📈 Resultado Final

```bash
$ ./mvnw test

Tests run: 46, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

**Taxa de sucesso**: 100% ✅

---

**Documento atualizado**: 2026-01-13 22:27
