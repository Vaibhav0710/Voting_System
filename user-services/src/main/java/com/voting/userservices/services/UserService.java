package com.voting.userservices.services;

import com.voting.userservices.dto.UserRequestDTO;
import com.voting.userservices.dto.UserResponseDTO;
import com.voting.userservices.entities.User;

import java.util.List;

public interface UserService {
    UserResponseDTO registerUser(UserRequestDTO request);
    String vote(Long userId, Long candidateId);
    UserResponseDTO getProfile(Long userId);

    User registerUser(User user);

    // ✅ Admin functionalities
    List<User> getAllUsers();
    List<User> getUsersByVotingStatus(boolean hasVoted);
}
