package com.aadiandjava.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aadiandjava.entity.Admin;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsernameIgnoreCase(String username);
    Optional<Admin> findByEmailIgnoreCase(String email);
}