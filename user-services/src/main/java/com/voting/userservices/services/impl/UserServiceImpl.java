package com.voting.userservices.services.impl;

import com.voting.userservices.dto.UserRequestDTO;
import com.voting.userservices.dto.UserResponseDTO;
import com.voting.userservices.entities.User;
import com.voting.userservices.feign.CandidateClient;
import com.voting.userservices.repository.UserRepository;
import com.voting.userservices.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CandidateClient candidateClient;

    @Override
    public UserResponseDTO registerUser(UserRequestDTO request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword()); // Password encryption in Phase 3
        user.setRole(request.getRole());
        user.setHasVoted(false);
        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    public String vote(Long userId, Long candidateId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isHasVoted()) {
            return "You have already voted!";
        }

        // Call candidate service to increment vote
        String candidateResponse = candidateClient.addVote(candidateId);

        // Mark user as voted
        user.setHasVoted(true);
        userRepository.save(user);

        return "Vote cast successfully! " + candidateResponse;
    }

    @Override
    public UserResponseDTO getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    @Override
    public User registerUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getUsersByVotingStatus(boolean hasVoted) {
        return userRepository.findByHasVoted(hasVoted);
    }

    private UserResponseDTO mapToResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isHasVoted()
        );
    }
}
