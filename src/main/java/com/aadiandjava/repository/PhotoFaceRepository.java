package com.aadiandjava.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aadiandjava.entity.PhotoFace;

public interface PhotoFaceRepository extends JpaRepository<PhotoFace, Long> {

    List<PhotoFace> findByPhoto_Album_Id(Long albumId);

    @Query("SELECT pf FROM PhotoFace pf WHERE pf.photo.id IN :photoIds")
    List<PhotoFace> findByPhotoIdIn(@Param("photoIds") List<Long> photoIds);

    @Override
    List<PhotoFace> findAll();
}