package com.aadiandjava.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aadiandjava.entity.Photo;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByAlbumIdOrderByUploadedAtDesc(Long albumId);

    List<Photo> findByUploadedByIgnoreCaseOrderByUploadedAtDesc(String uploadedBy);

    List<Photo> findAllByOrderByUploadedAtDesc();
}