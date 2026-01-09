package dev.mashni.habitsapi.controller;

import dev.mashni.habitsapi.dto.*;
import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import dev.mashni.habitsapi.service.HabitService;
import dev.mashni.habitsapi.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;
    private final UserService userService;

    public HabitController(HabitService habitService, UserService userService) {
        this.habitService = habitService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Habit> createHabit(
            @Valid @RequestBody CreateHabitRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var habit = habitService.createHabit(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(habit);
    }

    @PostMapping("/{id}/check")
    public ResponseEntity<String> checkHabit(
            @PathVariable UUID id,
            @RequestBody(required = false) CheckHabitRequest request,
            Authentication authentication) {

        if (request == null) {
            request = new CheckHabitRequest(null);
        }

        var user = userService.getUserFromAuthentication(authentication);
        var result = habitService.checkHabit(id, request, user);

        if (result == null) {
            return ResponseEntity.ok("Habit unchecked for date: " + request.date());
        } else {
            return ResponseEntity.ok("Habit checked for date: " + result.getCompletedDate());
        }
    }

    @GetMapping
    public ResponseEntity<List<HabitSummaryResponse>> getAllActiveHabits(Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var habits = habitService.getAllActiveHabits(user);
        return ResponseEntity.ok(habits);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HabitDetailResponse> getHabitDetail(
            @PathVariable UUID id,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var habit = habitService.getHabitDetail(id, user);
        return ResponseEntity.ok(habit);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHabit(@PathVariable UUID id) {
        habitService.deleteHabit(id);
        return ResponseEntity.ok().build();
    }

}
