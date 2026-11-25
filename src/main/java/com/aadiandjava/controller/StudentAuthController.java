package com.aadiandjava.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aadiandjava.entity.Student;
import com.aadiandjava.repository.StudentRepository;

@RestController
@RequestMapping("/api/students")
public class StudentAuthController {

    private final StudentRepository studentRepository;

    public StudentAuthController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody Map<String, String> req) {
        String name = req.get("name") != null ? req.get("name").trim() : "";
        String rollNo = req.get("rollNo") != null ? req.get("rollNo").trim() : "";
        String email = req.get("email") != null ? req.get("email").trim().toLowerCase() : "";
        String password = req.get("password") != null ? req.get("password") : "";

        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
        }
        if (rollNo.isBlank() && email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Roll Number or Email is required");
        }
        if (password.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 4 characters");
        }

        if (!rollNo.isBlank() && studentRepository.findByRollNoIgnoreCase(rollNo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Roll Number already registered");
        }
        if (!email.isBlank() && studentRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        Student student = new Student();
        student.setName(name);
        student.setRollNo(rollNo.isBlank() ? null : rollNo);
        student.setEmail(email.isBlank() ? null : email);
        student.setPassword(password);

        Student saved = studentRepository.save(student);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", saved.getId());
        out.put("name", saved.getName());
        out.put("rollNo", saved.getRollNo());
        out.put("email", saved.getEmail());
        out.put("faceEnrolled", isFaceEnrolled(saved));
        out.put("message", "Student registered in MySQL successfully");
        return out;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> req) {
        String rollNo = req.get("rollNo") != null ? req.get("rollNo").trim() : "";
        String email = req.get("email") != null ? req.get("email").trim().toLowerCase() : "";
        String password = req.get("password") != null ? req.get("password") : "";

        Optional<Student> studentOpt = !rollNo.isBlank()
                ? studentRepository.findByRollNoIgnoreCase(rollNo)
                : studentRepository.findByEmailIgnoreCase(email);

        Student student = studentOpt.orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student account not found")
        );

        if (student.getPassword() != null && !student.getPassword().equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", student.getId());
        out.put("name", student.getName());
        out.put("rollNo", student.getRollNo());
        out.put("email", student.getEmail());
        out.put("faceEnrolled", isFaceEnrolled(student));
        return out;
    }

    private boolean isFaceEnrolled(Student student) {
        try {
            var method = student.getClass().getMethod("getFaceEmbeddingJson");
            Object val = method.invoke(student);
            return val != null && !val.toString().isBlank();
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}