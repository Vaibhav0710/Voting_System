package com.voting.userservice.controller;

import com.voting.userservice.dto.UserDTO;
import com.voting.userservice.dto.UserRegisterDto;
import com.voting.userservice.service.UserServices;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    private UserServices userService;

    @PostMapping("/createUser")
    public ResponseEntity<UserDTO> registerUser(@RequestBody UserRegisterDto dto) {
        return ResponseEntity.ok(userService.createUser(dto));
    }

    @GetMapping("/getAllUsers")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
