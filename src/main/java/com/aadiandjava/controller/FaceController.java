package com.aadiandjava.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aadiandjava.service.FaceRecognitionService;

@RestController
@RequestMapping("/api/faces")
public class FaceController {

    private final FaceRecognitionService faceRecognitionService;

    public FaceController(FaceRecognitionService faceRecognitionService) {
        this.faceRecognitionService = faceRecognitionService;
    }

    @PostMapping(value = "/enroll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> enrollStudent(
            @RequestParam("name") String name,
            @RequestParam(value = "rollNo", required = false) String rollNo,
            @RequestParam("faceImage") MultipartFile faceImage
    ) {
        var result = faceRecognitionService.enrollStudent(name, rollNo, faceImage);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/verify-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> verifyFaceAndGetPhotos(
            @RequestParam("liveFace") MultipartFile liveFace,
            @RequestParam(value = "albumId", required = false) Long albumId,
            @RequestParam(value = "rollNo", required = false) String rollNo
    ) {
        var result = faceRecognitionService.verifyFaceAndGetPhotos(liveFace, albumId, rollNo);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/albums/{albumId}/index-faces")
    public ResponseEntity<?> indexAlbumFaces(@PathVariable Long albumId) {
        var result = faceRecognitionService.indexAlbumFaces(albumId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<?> checkEngineStatus() {
        var result = faceRecognitionService.checkEngineStatus();
        return ResponseEntity.ok(result);
    }
}