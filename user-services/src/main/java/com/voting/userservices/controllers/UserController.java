package com.voting.userservices.controllers;


import com.voting.userservices.dto.AuthResponseDTO;
import com.voting.userservices.dto.LoginRequestDTO;
import com.voting.userservices.dto.RegisterRequestDTO;
import com.voting.userservices.services.UserServices;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class UserController {

    private final UserServices userService;

    @Autowired
    public UserController(UserServices userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registerUser(@Valid @RequestBody RegisterRequestDTO userRegisterDTO) {
        return ResponseEntity.ok(userService.registerUser(userRegisterDTO));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> loginUser(@Valid @RequestBody LoginRequestDTO userLoginDTO) {
        return ResponseEntity.ok(userService.loginUser(userLoginDTO));
    }
}
