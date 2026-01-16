package dev.mashni.habitsapi.challenge;

import dev.mashni.habitsapi.challenge.dto.*;
import dev.mashni.habitsapi.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing challenges (squads).
 * Provides endpoints for creating, joining, and viewing challenge details.
 */
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final UserService userService;

    public ChallengeController(ChallengeService challengeService, UserService userService) {
        this.challengeService = challengeService;
        this.userService = userService;
    }

    /**
     * Create a new challenge.
     * Only PRO users can create challenges.
     */
    @PostMapping
    public ResponseEntity<ChallengeResponse> createChallenge(
            @Valid @RequestBody CreateChallengeRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var response = challengeService.createChallenge(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Join a challenge using an invite code.
     * Any user can join a challenge.
     */
    @PostMapping("/join")
    public ResponseEntity<ChallengeResponse> joinChallenge(
            @Valid @RequestBody JoinChallengeRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var response = challengeService.joinChallenge(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all challenges the user is participating in.
     */
    @GetMapping
    public ResponseEntity<List<ChallengeResponse>> getUserChallenges(Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var challenges = challengeService.getUserChallenges(user);
        return ResponseEntity.ok(challenges);
    }

    /**
     * Get detailed information about a challenge including leaderboard.
     * Only participants can view challenge details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChallengeDetailResponse> getChallengeDetail(
            @PathVariable UUID id,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var response = challengeService.getChallengeDetail(id, user);
        return ResponseEntity.ok(response);
    }
}
