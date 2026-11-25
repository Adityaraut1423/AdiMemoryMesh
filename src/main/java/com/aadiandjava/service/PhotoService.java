package com.aadiandjava.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.aadiandjava.entity.Album;
import com.aadiandjava.entity.Photo;
import com.aadiandjava.repository.PhotoRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Service
public class PhotoService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final int MAX_UPLOAD_RETRIES = 3;

    private final Cloudinary cloudinary;
    private final PhotoRepository photoRepository;
    private final AlbumService albumService;
    private final FaceRecognitionService faceRecognitionService;

    @Value("${cloudinary.upload-preset:}")
    private String uploadPreset;

    @Value("${cloudinary.connect-timeout:30000}")
    private int connectTimeoutMs;

    @Value("${cloudinary.read-timeout:60000}")
    private int readTimeoutMs;

    public PhotoService(
            Cloudinary cloudinary,
            PhotoRepository photoRepository,
            AlbumService albumService,
            FaceRecognitionService faceRecognitionService
    ) {
        this.cloudinary = cloudinary;
        this.photoRepository = photoRepository;
        this.albumService = albumService;
        this.faceRecognitionService = faceRecognitionService;
    }

    public List<Photo> uploadPhotos(
            Long albumId,
            MultipartFile[] files,
            String uploadedBy,
            String caption,
            String taggedStudents
    ) {
        Album album = albumService.getAlbumById(albumId);
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image file is required");
        }

        List<Photo> savedPhotos = new ArrayList<>();
        String folder = "college-photos/album_" + albumId;
        String cleanUploader = (uploadedBy != null && !uploadedBy.isBlank())
                ? uploadedBy.trim()
                : "Anonymous Friend";
        String cleanCaption = (caption != null && !caption.isBlank())
                ? caption.trim()
                : null;
        String cleanTaggedStudents = (taggedStudents != null && !taggedStudents.isBlank())
                ? taggedStudents.trim()
                : null;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported file type for '" + file.getOriginalFilename()
                                + "'. Only JPEG, PNG, WEBP, and GIF are allowed."
                );
            }

            try {
                byte[] fileBytes = file.getBytes();
                Map<String, Object> options = buildUploadOptions(folder);
                Map<String, Object> uploadResult = uploadWithRetry(file.getOriginalFilename(), fileBytes, options);

                Object secureUrl = uploadResult.get("secure_url");
                Object publicId = uploadResult.get("public_id");
                if (secureUrl == null || publicId == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Cloudinary upload returned incomplete metadata"
                    );
                }

                Photo photo = new Photo();
                photo.setImageUrl(secureUrl.toString());
                photo.setPublicId(publicId.toString());
                photo.setUploadedBy(cleanUploader);
                photo.setCaption(cleanCaption);
                photo.setTaggedStudents(cleanTaggedStudents);
                photo.setAlbum(album);

                Photo savedPhoto = photoRepository.save(photo);
                savedPhotos.add(savedPhoto);

                try {
                    faceRecognitionService.indexFacesForPhoto(savedPhoto, file);
                } catch (Exception ignored) {
                    // Keep upload successful even if face engine is temporarily offline
                }
            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (IOException | RuntimeException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to upload '" + file.getOriginalFilename() + "' to Cloudinary: " + e.getMessage(),
                        e
                );
            }
        }

        return savedPhotos;
    }

    private Map<String, Object> buildUploadOptions(String folder) {
        Map<String, Object> options = new HashMap<>();
        options.put("folder", folder);
        options.put("resource_type", "image");
        options.put("connect_timeout", connectTimeoutMs);
        options.put("read_timeout", readTimeoutMs);

        if (uploadPreset != null && !uploadPreset.isBlank()) {
            options.put("upload_preset", uploadPreset.trim());
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> uploadWithRetry(
            String fileName,
            byte[] fileBytes,
            Map<String, Object> options
    ) throws IOException {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_UPLOAD_RETRIES; attempt++) {
            try {
                return cloudinary.uploader().upload(fileBytes, options);
            } catch (IOException | RuntimeException ex) {
                lastException = ex;
                if (attempt == MAX_UPLOAD_RETRIES) {
                    break;
                }
                try {
                    Thread.sleep(700L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Upload interrupted for '" + fileName + "'", ie);
                }
            }
        }

        if (lastException instanceof IOException ioEx) {
            throw ioEx;
        }
        throw new IOException(
                lastException != null ? lastException.getMessage() : "Unknown Cloudinary upload error",
                lastException
        );
    }

    public List<Photo> getPhotosByAlbum(Long albumId, String uploader, String student) {
        albumService.getAlbumById(albumId);
        return photoRepository.findByAlbumIdOrderByUploadedAtDesc(albumId).stream()
                .filter(p -> {
                    if (uploader != null && !uploader.isBlank()) {
                        return p.getUploadedBy() != null
                                && p.getUploadedBy().equalsIgnoreCase(uploader.trim());
                    }
                    return true;
                })
                .filter(p -> isStudentInPhoto(p, student))
                .toList();
    }

    public List<Photo> getPhotosByUploader(String uploader, String student) {
        if (uploader == null || uploader.isBlank()) {
            return List.of();
        }
        return photoRepository.findByUploadedByIgnoreCaseOrderByUploadedAtDesc(uploader.trim()).stream()
                .filter(p -> isStudentInPhoto(p, student))
                .toList();
    }

    public List<Photo> getPhotosForStudent(String student) {
        if (student == null || student.isBlank()) {
            return List.of();
        }
        return photoRepository.findAllByOrderByUploadedAtDesc().stream()
                .filter(p -> isStudentInPhoto(p, student))
                .toList();
    }

    public void deletePhoto(Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found: " + photoId));

        if (photo.getPublicId() != null && !photo.getPublicId().isBlank()) {
            try {
                cloudinary.uploader().destroy(photo.getPublicId(), ObjectUtils.emptyMap());
            } catch (IOException | RuntimeException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to delete image from cloud storage",
                        e
                );
            }
        }

        photoRepository.delete(photo);
    }

    private boolean isStudentInPhoto(Photo photo, String studentName) {
        if (studentName == null || studentName.isBlank()) {
            return true;
        }
        String tagged = photo.getTaggedStudents();
        if (tagged == null || tagged.isBlank()) {
            return false;
        }
        String target = studentName.trim().toLowerCase();
        for (String person : tagged.split(",")) {
            if (person.trim().toLowerCase().equals(target)) {
                return true;
            }
        }
        return false;
    }
}