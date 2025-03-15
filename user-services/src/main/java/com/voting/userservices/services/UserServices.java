package com.voting.userservices.services;

import com.voting.userservices.dto.AuthResponseDTO;
import com.voting.userservices.dto.LoginRequestDTO;
import com.voting.userservices.dto.RegisterRequestDTO;
import com.voting.userservices.entities.User;
import com.voting.userservices.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServices {
    private final UserRepository userRepository;

    @Autowired
    public UserServices(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthResponseDTO registerUser(RegisterRequestDTO userRegisterDTO) {
        // Check if user already exists
        if (userRepository.findByEmail(userRegisterDTO.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered!");
        }

        // Create user entity
        User user = new User();
        user.setName(userRegisterDTO.getName());
        user.setEmail(userRegisterDTO.getEmail());
        user.setPassword(userRegisterDTO.getPassword()); // No encryption for now
        user.setRole(userRegisterDTO.getRole());

        User savedUser = userRepository.save(user);

        return new AuthResponseDTO(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                "User registered successfully!"
        );
    }

    public AuthResponseDTO loginUser(LoginRequestDTO userLoginDTO) {
        Optional<User> userOptional = userRepository.findByEmail(userLoginDTO.getEmail());
        if (userOptional.isEmpty() || !userOptional.get().getPassword().equals(userLoginDTO.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        User user = userOptional.get();
        return new AuthResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                "Login successful!"
        );
    }
}
