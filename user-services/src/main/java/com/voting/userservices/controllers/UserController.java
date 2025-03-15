package com.voting.userservices.controllers;

import com.voting.userservices.entities.User;
import com.voting.userservices.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/voting/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ✅ List all users (Admin functionality)
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ✅ List users who have voted
    @GetMapping("/voted")
    public ResponseEntity<List<User>> getUsersWhoVoted() {
        return ResponseEntity.ok(userService.getUsersByVotingStatus(true));
    }

    // ✅ List users who haven't voted
    @GetMapping("/not-voted")
    public ResponseEntity<List<User>> getUsersWhoHaveNotVoted() {
        return ResponseEntity.ok(userService.getUsersByVotingStatus(false));
    }
}
