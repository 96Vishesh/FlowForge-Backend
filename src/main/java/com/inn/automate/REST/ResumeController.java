package com.inn.automate.REST;


import com.inn.automate.Service.ResumeTransformationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@Slf4j
@CrossOrigin
public class ResumeController {

    @Autowired
    private ResumeTransformationService ResumeService;

    /**
     * Transform resume using Gemini AI
     *
     * @param file - Resume PDF file
     * @param jobDescription - Target job description
     * @return Transformed resume file path
     */
    @PostMapping(value = "/transform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> transformResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("message", "Please upload a PDF file");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if file is PDF
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                response.put("message", "Only PDF files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file size (10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                response.put("message", "File size exceeds 10MB limit");
                return ResponseEntity.badRequest().body(response);
            }

            // Process resume with Gemini
            log.info("Processing resume with Gemini: {}", file.getOriginalFilename());
            String outputPath = ResumeService.transformResume(file, jobDescription);

            response.put("message", "Resume transformed successfully using Gemini AI");
            response.put("filePath", outputPath);
            response.put("downloadUrl", "/api/resume/gemini/download?path=" + outputPath);
            response.put("aiModel", "Gemini 1.5 Pro");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error transforming resume with Gemini", e);
            response.put("message", "Error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download transformed resume
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadResume(@RequestParam("path") String filePath) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=" + file.getName());
            headers.add(HttpHeaders.CONTENT_TYPE, "application/pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading resume", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Gemini Resume Transformation Service");
        response.put("aiProvider", "Google Gemini");
        return ResponseEntity.ok(response);
    }
}