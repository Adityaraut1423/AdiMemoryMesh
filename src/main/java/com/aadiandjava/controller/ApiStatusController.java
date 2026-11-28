package com.aadiandjava.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiStatusController {

    @GetMapping({"/", "/api", "/api/"})
    public Map<String, String> status() {
        return Map.of(
                "status", "UP",
                "service", "AdiMemoryMesh",
                "baseApi", "/api"
        );
    }
}