package com.voting.userservice.service;

import com.voting.userservice.dto.UserDTO;
import com.voting.userservice.dto.UserRegisterDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserServices {
    UserDTO createUser(UserRegisterDto UserDTO);
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO updateUser(Long id, UserDTO userDTO);
    void deleteUser(Long id);
    void markUserAsVoted(Long id);
}
