package com.aadiandjava.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.aadiandjava.entity.Photo;
import com.aadiandjava.service.PhotoService;

@RestController
@RequestMapping("/api")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(value = "/albums/{albumId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<Photo>> uploadPhotos(
            @PathVariable Long albumId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "taggedStudents", required = false) String taggedStudents
    ) {
        boolean hasAnyFile = files != null && Arrays.stream(files).anyMatch(f -> f != null && !f.isEmpty());
        if (!hasAnyFile) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one non-empty photo file is required");
        }

        String resolvedUploader = (uploadedBy != null && !uploadedBy.isBlank())
                ? uploadedBy.trim()
                : "Student";

        List<Photo> saved = photoService.uploadPhotos(
                albumId,
                files,
                resolvedUploader,
                caption != null && !caption.isBlank() ? caption.trim() : null,
                taggedStudents != null && !taggedStudents.isBlank() ? taggedStudents.trim() : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/albums/{albumId}/photos")
    public ResponseEntity<List<Photo>> getPhotosByAlbum(
            @PathVariable Long albumId,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            @RequestParam(value = "student", required = false) String student
    ) {
        return ResponseEntity.ok(photoService.getPhotosByAlbum(albumId, uploadedBy, student));
    }

    @GetMapping("/photos/search")
    public ResponseEntity<List<Photo>> searchPhotosByUploader(
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            @RequestParam(value = "student", required = false) String student
    ) {
        if (uploadedBy != null && !uploadedBy.isBlank()) {
            return ResponseEntity.ok(photoService.getPhotosByUploader(uploadedBy, student));
        }
        if (student != null && !student.isBlank()) {
            return ResponseEntity.ok(photoService.getPhotosForStudent(student));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/photos/for-student")
    public ResponseEntity<List<Photo>> getPhotosForStudent(@RequestParam("student") String student) {
        return ResponseEntity.ok(photoService.getPhotosForStudent(student));
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long photoId) {
        photoService.deletePhoto(photoId);
        return ResponseEntity.noContent().build();
    }
}