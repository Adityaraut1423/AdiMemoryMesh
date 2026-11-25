package com.aadiandjava.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "photo_faces")
public class PhotoFace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    @JsonIgnore
    private Photo photo;

    @Column(name = "face_embedding_json", columnDefinition = "LONGTEXT", nullable = false)
    private String faceEmbeddingJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Photo getPhoto() { return photo; }
    public void setPhoto(Photo photo) { this.photo = photo; }

    public String getFaceEmbeddingJson() { return faceEmbeddingJson; }
    public void setFaceEmbeddingJson(String faceEmbeddingJson) { this.faceEmbeddingJson = faceEmbeddingJson; }
}