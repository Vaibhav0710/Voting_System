package com.voting.userservice.service;

import com.voting.userservice.dto.UserDTO;
import com.voting.userservice.dto.UserRegisterDto;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserServices {
    UserDTO createUser(UserRegisterDto userRegisterDto);
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
}
