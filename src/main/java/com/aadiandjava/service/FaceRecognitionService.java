package com.aadiandjava.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.aadiandjava.entity.Photo;
import com.aadiandjava.entity.PhotoFace;
import com.aadiandjava.entity.Student;
import com.aadiandjava.repository.PhotoFaceRepository;
import com.aadiandjava.repository.PhotoRepository;
import com.aadiandjava.repository.StudentRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class FaceRecognitionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final StudentRepository studentRepository;
    private final PhotoRepository photoRepository;
    private final PhotoFaceRepository photoFaceRepository;
    private final Cloudinary cloudinary;

    @Value("${face-engine.url:http://127.0.0.1:8001}")
    private String faceEngineUrl;

    @Value("${face-engine.match-threshold:0.45}")
    private double matchThreshold;

    public FaceRecognitionService(
            StudentRepository studentRepository,
            PhotoRepository photoRepository,
            PhotoFaceRepository photoFaceRepository,
            Cloudinary cloudinary
    ) {
        this.studentRepository = studentRepository;
        this.photoRepository = photoRepository;
        this.photoFaceRepository = photoFaceRepository;
        this.cloudinary = cloudinary;
    }

    public Map<String, Object> checkEngineStatus() {
        try {
            ResponseEntity res = restTemplate.getForEntity(faceEngineUrl + "/health", Map.class);
            Map body = (Map) res.getBody();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("statusCode", res.getStatusCode().value());
            out.put("engineResponse", body);
            return out;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Face engine unreachable: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> indexAlbumFaces(Long albumId) {
        List<Photo> photos = photoRepository.findByAlbumIdOrderByUploadedAtDesc(albumId);
        int indexedFaces = 0;

        for (Photo photo : photos) {
            if (photo.getImageUrl() == null || photo.getImageUrl().isBlank()) {
                continue;
            }
            try {
                List<String> embeddings = fetchAndExtractEmbeddingsFromUrl(photo);
                for (String embJson : embeddings) {
                    PhotoFace pf = new PhotoFace();
                    pf.setPhoto(photo);
                    pf.setFaceEmbeddingJson(embJson);
                    photoFaceRepository.save(pf);
                    indexedFaces++;
                }
            } catch (Exception ignored) {
                // Skip unreachable or no-face photos during batch indexing
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albumId", albumId);
        result.put("photosScanned", photos.size());
        result.put("facesIndexed", indexedFaces);
        return result;
    }

    public Map<String, Object> enrollStudent(String name, String rollNo, MultipartFile faceImage) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student name is required");
        }
        if (faceImage == null || faceImage.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Face image is required");
        }

        // Validate that a clear single face is detected
        extractSingleEmbeddingJson(faceImage);

        String imageUrl;
        try {
            Map uploadResult = cloudinary.uploader().upload(
                    faceImage.getBytes(),
                    ObjectUtils.asMap("folder", "college-photos/student-faces", "resource_type", "image")
            );
            imageUrl = String.valueOf(uploadResult.get("secure_url"));
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to save reference face image", e);
        }

        Student student = (rollNo != null && !rollNo.isBlank())
                ? studentRepository.findByRollNoIgnoreCase(rollNo.trim()).orElseGet(Student::new)
                : new Student();

        student.setName(name.trim());
        student.setRollNo(rollNo != null && !rollNo.isBlank() ? rollNo.trim() : null);
        student.setReferenceImageUrl(imageUrl);

        Student saved = studentRepository.save(student);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("name", saved.getName());
        response.put("rollNo", saved.getRollNo());
        response.put("referenceImageUrl", saved.getReferenceImageUrl());
        response.put("message", "Student face enrolled successfully");
        return response;
    }

    public void indexFacesForPhoto(Photo photo, MultipartFile file) {
        try {
            List<String> faceEmbeddings = extractAllEmbeddingsFromBytes(file.getBytes(), file.getOriginalFilename());
            for (String embJson : faceEmbeddings) {
                PhotoFace pf = new PhotoFace();
                pf.setPhoto(photo);
                pf.setFaceEmbeddingJson(embJson);
                photoFaceRepository.save(pf);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded image bytes", e);
        }
    }

    public Map<String, Object> verifyFaceAndGetPhotos(MultipartFile liveFace, Long albumId) {
        return verifyFaceAndGetPhotos(liveFace, albumId, null);
    }

    public Map<String, Object> verifyFaceAndGetPhotos(MultipartFile liveFace, Long albumId, String rollNo) {
        if (liveFace == null || liveFace.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live face image is required");
        }

        List liveEmbedding = extractSingleEmbeddingList(liveFace);

        List<Photo> candidatePhotos = (albumId != null)
                ? photoRepository.findByAlbumIdOrderByUploadedAtDesc(albumId)
                : photoRepository.findAllByOrderByUploadedAtDesc();

        List<Photo> matchedPhotos = filterPhotosContainingFace(candidatePhotos, liveEmbedding);

        if (matchedPhotos.isEmpty()) {
            Map<String, Object> noMatch = new LinkedHashMap<>();
            noMatch.put("verified", false);
            noMatch.put("message", "No photos found containing this scanned face");
            noMatch.put("matchedPhotoCount", 0);
            noMatch.put("photos", List.of());
            return noMatch;
        }

        String displayName = "Scanned Student";
        String displayRollNo = (rollNo != null && !rollNo.isBlank()) ? rollNo.trim() : null;
        Long matchedStudentId = null;

        if (rollNo != null && !rollNo.isBlank()) {
            var opt = studentRepository.findByRollNoIgnoreCase(rollNo.trim());
            if (opt.isPresent()) {
                displayName = opt.get().getName();
                displayRollNo = opt.get().getRollNo();
                matchedStudentId = opt.get().getId();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verified", true);
        result.put("studentId", matchedStudentId);
        result.put("studentName", displayName);
        result.put("rollNo", displayRollNo);
        result.put("matchedPhotoCount", matchedPhotos.size());
        result.put("photos", matchedPhotos);
        return result;
    }

    private List<Photo> filterPhotosContainingFace(List<Photo> candidatePhotos, List liveEmbedding) {
        if (candidatePhotos == null || candidatePhotos.isEmpty()) {
            return List.of();
        }

        List<Long> photoIds = new ArrayList<>(candidatePhotos.size());
        for (Photo p : candidatePhotos) {
            if (p.getId() != null) {
                photoIds.add(p.getId());
            }
        }
        if (photoIds.isEmpty()) {
            return List.of();
        }

        // Only load PhotoFace rows for the candidate photos (avoid full-table findAll())
        Map<Long, List<PhotoFace>> facesByPhotoId = new HashMap<>();
        for (PhotoFace pf : photoFaceRepository.findByPhotoIdIn(photoIds)) {
            if (pf.getPhoto() != null && pf.getPhoto().getId() != null) {
                facesByPhotoId.computeIfAbsent(pf.getPhoto().getId(), k -> new ArrayList<>()).add(pf);
            }
        }

        List faceCandidates = new ArrayList();
        Map<Long, Long> candidateIdToPhotoId = new HashMap<>();
        long syntheticCandidateId = 1L;

        for (Photo photo : candidatePhotos) {
            Long photoId = photo.getId();
            List<PhotoFace> storedFaces = (photoId != null) ? facesByPhotoId.get(photoId) : null;
            if (storedFaces == null || storedFaces.isEmpty()) {
                continue;
            }
            for (PhotoFace pf : storedFaces) {
                List emb = parseFloatList(pf.getFaceEmbeddingJson());
                if (!emb.isEmpty()) {
                    long cid = syntheticCandidateId++;
                    Map item = new LinkedHashMap();
                    item.put("id", cid);
                    item.put("embedding", emb);
                    faceCandidates.add(item);
                    candidateIdToPhotoId.put(cid, photoId);
                }
            }
        }

        if (faceCandidates.isEmpty()) {
            return List.of();
        }

        Map matchResult = compareEmbeddings(liveEmbedding, faceCandidates, matchThreshold);
        Set<Long> matchedPhotoIds = new LinkedHashSet<>();
        for (Long cid : readMatchedIds(matchResult)) {
            Long pid = candidateIdToPhotoId.get(cid);
            if (pid != null) {
                matchedPhotoIds.add(pid);
            }
        }

        List<Photo> filtered = new ArrayList<>();
        for (Photo p : candidatePhotos) {
            if (p.getId() != null && matchedPhotoIds.contains(p.getId())) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    private List<String> fetchAndExtractEmbeddingsFromUrl(Photo photo) {
        try {
            HttpRequest imgReq = HttpRequest.newBuilder()
                    .uri(URI.create(photo.getImageUrl()))
                    .GET()
                    .build();
            HttpResponse<byte[]> imgRes = httpClient.send(imgReq, HttpResponse.BodyHandlers.ofByteArray());
            if (imgRes.statusCode() == 200 && imgRes.body() != null && imgRes.body().length > 0) {
                return extractAllEmbeddingsFromBytes(imgRes.body(), "photo_" + photo.getId() + ".jpg");
            }
        } catch (Exception ignored) {
            // Ignore unreachable photo during scan
        }
        return List.of();
    }

    private List extractSingleEmbeddingList(MultipartFile file) {
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read face image", e);
        }

        try {
            HttpEntity entity = buildMultipartBytesEntity(
                    fileBytes,
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "face.jpg",
                    "image"
            );
            ResponseEntity res = restTemplate.postForEntity(
                    faceEngineUrl + "/extract-single",
                    entity,
                    Map.class
            );
            Map body = (Map) res.getBody();
            Object embObj = body != null ? body.get("embedding") : null;
            if (!(embObj instanceof List)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid embedding response from face engine");
            }
            List rawList = (List) embObj;
            List out = new ArrayList(rawList.size());
            for (Object num : rawList) {
                out.add(((Number) num).doubleValue());
            }
            return out;
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No clear single face detected in image");
        } catch (RuntimeException e) {
            if (e instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Face engine unavailable: " + e.getMessage(), e);
        }
    }

    private String extractSingleEmbeddingJson(MultipartFile file) {
        List values = extractSingleEmbeddingList(file);
        return toJsonFloatArray(values);
    }

    private List<String> extractAllEmbeddingsFromBytes(byte[] bytes, String filename) {
        try {
            HttpEntity entity = buildMultipartBytesEntity(
                    bytes,
                    filename != null && !filename.isBlank() ? filename : "photo.jpg",
                    "image"
            );
            ResponseEntity res = restTemplate.postForEntity(
                    faceEngineUrl + "/extract-all",
                    entity,
                    Map.class
            );
            Map body = (Map) res.getBody();
            Object embsObj = body != null ? body.get("embeddings") : null;
            if (!(embsObj instanceof List)) {
                return List.of();
            }
            List rawEmbeddings = (List) embsObj;
            List<String> list = new ArrayList<>();
            for (Object singleEmb : rawEmbeddings) {
                if (singleEmb instanceof List) {
                    List vec = (List) singleEmb;
                    List nums = new ArrayList(vec.size());
                    for (Object n : vec) {
                        nums.add(((Number) n).doubleValue());
                    }
                    list.add(toJsonFloatArray(nums));
                }
            }
            return list;
        } catch (HttpClientErrorException e) {
            return List.of();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Face indexing failed: " + e.getMessage(), e);
        }
    }

    private Map compareEmbeddings(List queryEmbedding, List candidates, double threshold) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of("matched", false, "matched_ids", List.of());
        }
        try {
            Map body = new LinkedHashMap();
            body.put("query_embedding", queryEmbedding);
            body.put("candidates", candidates);
            body.put("threshold", threshold);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity entity = new HttpEntity(body, headers);
            ResponseEntity res = restTemplate.postForEntity(
                    faceEngineUrl + "/compare",
                    entity,
                    Map.class
            );
            Map responseBody = (Map) res.getBody();
            return responseBody != null ? responseBody : Map.of();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Face comparison failed: " + e.getMessage(), e);
        }
    }

    private HttpEntity buildMultipartBytesEntity(byte[] bytes, String filename, String fieldName) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(fieldName, resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private String toJsonFloatArray(List values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private List parseFloatList(String jsonArrayStr) {
        if (jsonArrayStr == null || jsonArrayStr.isBlank()) {
            return List.of();
        }
        String trimmed = jsonArrayStr.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] parts = trimmed.split(",");
        List list = new ArrayList(parts.length);
        for (String p : parts) {
            String token = p.trim();
            if (!token.isEmpty()) {
                list.add(Double.parseDouble(token));
            }
        }
        return list;
    }

    private List<Long> readMatchedIds(Map matchNode) {
        if (matchNode == null) {
            return List.of();
        }
        Object idsObj = matchNode.get("matched_ids");
        if (!(idsObj instanceof List)) {
            return List.of();
        }
        List rawIds = (List) idsObj;
        List<Long> ids = new ArrayList<>();
        for (Object item : rawIds) {
            if (item instanceof Number num) {
                ids.add(num.longValue());
            } else if (item instanceof String str && !str.isBlank()) {
                ids.add(Long.parseLong(str.trim()));
            }
        }
        return ids;
    }
}