package com.aadiandjava.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aadiandjava.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    List<Album> findAllByOrderByEventDateDescIdDesc();
}