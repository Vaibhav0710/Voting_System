package com.voting.userservice.service.impl;

import com.voting.userservice.dto.UserDTO;
import com.voting.userservice.dto.UserRegisterDto;
import com.voting.userservice.entity.User;
import com.voting.userservice.repository.UserRepository;
import com.voting.userservice.service.UserServices;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserServicesImpl implements UserServices {

    private UserRepository userRepository;

    @Override
    public UserDTO createUser(UserRegisterDto dto) {
        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setRole(dto.getRole());
        user.setCreatedAt(LocalDateTime.now());
        return mapToDto(userRepository.save(user));
    }

    @Override
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : users) {
            UserDTO dto = mapToDto(user);
            userDTOs.add(dto);
        }
        return userDTOs;
    }


    @Override
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return mapToDto(user);
    }


    private UserDTO mapToDto(User user) {
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}
