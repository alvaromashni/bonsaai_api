package dev.mashni.habitsapi.controller;

import dev.mashni.habitsapi.dto.*;
import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import dev.mashni.habitsapi.service.HabitService;
import dev.mashni.habitsapi.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public ResponseEntity<HabitResponse> createHabit(
            @Valid @RequestBody CreateHabitRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var habit = habitService.createHabit(request, user);
        var response = new HabitResponse(
            habit.getId(),
            habit.getName(),
            habit.getDescription(),
            habit.getStartDate(),
            habit.getStatus().name(),
            habit.getFrequencyType(),
            habit.getTargetDays()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
    public ResponseEntity<Page<HabitSummaryResponse>> getAllActiveHabits(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var user = userService.getUserFromAuthentication(authentication);
        Pageable pageable = PageRequest.of(page, size);
        var habits = habitService.getAllActiveHabits(user, pageable);
        return ResponseEntity.ok(habits);
    }

    @GetMapping("/archived")
    public ResponseEntity<Page<HabitSummaryResponse>> getAllArchivedHabits(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var user = userService.getUserFromAuthentication(authentication);
        Pageable pageable = PageRequest.of(page, size);
        var habits = habitService.getAllArchivedHabits(user, pageable);
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

    @PutMapping("/{id}")
    public ResponseEntity<HabitResponse> updateHabit(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHabitRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var updatedHabit = habitService.updateHabit(id, request, user);
        var response = new HabitResponse(
            updatedHabit.getId(),
            updatedHabit.getName(),
            updatedHabit.getDescription(),
            updatedHabit.getStartDate(),
            updatedHabit.getStatus().name(),
            updatedHabit.getFrequencyType(),
            updatedHabit.getTargetDays()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHabit(@PathVariable UUID id, Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        habitService.deleteHabit(id, user);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<HabitResponse> archiveHabit(@PathVariable UUID id, Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var archivedHabit = habitService.archiveHabit(id, user);
        var response = new HabitResponse(
            archivedHabit.getId(),
            archivedHabit.getName(),
            archivedHabit.getDescription(),
            archivedHabit.getStartDate(),
            archivedHabit.getStatus().name(),
            archivedHabit.getFrequencyType(),
            archivedHabit.getTargetDays()
        );
        return ResponseEntity.ok(response);
    }

}
