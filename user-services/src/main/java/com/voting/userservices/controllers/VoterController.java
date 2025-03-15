package com.voting.userservices.controllers;

import com.voting.userservices.dto.UserRequestDTO;
import com.voting.userservices.dto.UserResponseDTO;
import com.voting.userservices.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voting/users")
@RequiredArgsConstructor
public class VoterController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@RequestBody UserRequestDTO request) {
        return ResponseEntity.ok(userService.registerUser(request));
    }

    @PostMapping("/vote")
    public ResponseEntity<String> vote(@RequestParam Long userId, @RequestParam Long candidateId) {
        return ResponseEntity.ok(userService.vote(userId, candidateId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDTO> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }
}
