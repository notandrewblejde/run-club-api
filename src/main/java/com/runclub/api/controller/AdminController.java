package com.runclub.api.controller;

import com.runclub.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * TEMPORARY admin controller for one-time DB cleanup.
 * DELETE THIS FILE after use.
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @DeleteMapping("/users/by-auth0/{auth0Id}")
    public ResponseEntity<?> deleteUserByAuth0Id(@PathVariable String auth0Id) {
        var user = userRepository.findByAuth0Id(auth0Id);
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        userRepository.delete(user.get());
        return ResponseEntity.ok().body("{\"deleted\": true, \"auth0Id\": \"" + auth0Id + "\"}");
    }
}
