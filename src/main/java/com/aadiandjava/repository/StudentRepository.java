package com.aadiandjava.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.aadiandjava.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByRollNoIgnoreCase(String rollNo);
    Optional<Student> findByEmailIgnoreCase(String email);
}