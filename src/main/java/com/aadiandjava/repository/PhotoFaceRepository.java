package com.aadiandjava.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aadiandjava.entity.PhotoFace;

public interface PhotoFaceRepository extends JpaRepository<PhotoFace, Long> {
    List<PhotoFace> findByPhoto_Album_Id(Long albumId);
    @Override
	List<PhotoFace> findAll();
}