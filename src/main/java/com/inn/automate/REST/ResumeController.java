//package com.inn.automate.REST;
//
//import com.inn.automate.POJO.TemplateDTO;
//import com.inn.automate.Service.ResumeTransformationService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.core.io.FileSystemResource;
//import org.springframework.core.io.Resource;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/resume")
//@Slf4j
//@CrossOrigin
//public class ResumeController {
//
//    @Autowired
//    private ResumeTransformationService resumeService;
//
//    /**
//     * Step 1: Extract and transform resume to JSON
//     * Returns structured JSON without generating PDF
//     */
//    @PostMapping(value = "/transform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<Map<String, Object>> transformResume(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("jobDescription") String jobDescription) {
//
//        Map<String, Object> response = new HashMap<>();
//
//        try {
//            // Validate file
//            if (file == null || file.isEmpty()) {
//                response.put("message", "Please upload a PDF file");
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            // Check if file is PDF
//            String contentType = file.getContentType();
//            if (contentType == null || !contentType.equals("application/pdf")) {
//                response.put("message", "Only PDF files are allowed. Received: " + contentType);
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            // Validate file size (10MB limit)
//            if (file.getSize() > 10 * 1024 * 1024) {
//                response.put("message", "File size exceeds 10MB limit");
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            // Validate job description
//            if (jobDescription == null || jobDescription.trim().isEmpty()) {
//                response.put("message", "Job description is required");
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            log.info("📤 Processing resume: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
//
//            // Transform resume to JSON
//            String transformedJson = resumeService.transformResume(file, jobDescription);
//
//            response.put("success", true);
//            response.put("message", "Resume extracted and transformed successfully");
//            response.put("transformedData", transformedJson); // JSON string
//            response.put("nextStep", "GET /api/resume/templates to view available templates");
//            response.put("finalStep", "POST /api/resume/generate-pdf with transformedData and templateId");
//
//            log.info("✅ Resume transformation complete");
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            log.error("❌ Error transforming resume", e);
//            response.put("success", false);
//            response.put("message", "Error: " + e.getMessage());
//            response.put("error", e.getClass().getSimpleName());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }
//
//    /**
//     * Step 2: Get available templates from database
//     */
//    @GetMapping("/templates")
//    public ResponseEntity<Map<String, Object>> getAvailableTemplates() {
//        Map<String, Object> response = new HashMap<>();
//
//        try {
//            log.info("📋 Fetching available templates...");
//
//            List<TemplateDTO> templates = resumeService.getAvailableTemplates();
//
//            response.put("success", true);
//            response.put("message", "Templates retrieved successfully");
//            response.put("count", templates.size());
//            response.put("templates", templates);
//
//            log.info("✅ Retrieved {} templates", templates.size());
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            log.error("❌ Error retrieving templates", e);
//            response.put("success", false);
//            response.put("message", "Error: " + e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }
//
//    /**
//     * Step 3: Generate PDF with selected template
//     */
//    @PostMapping(value = "/generate-pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<Map<String, Object>> generatePdf(
//            @RequestBody Map<String, String> request) {
//
//        Map<String, Object> response = new HashMap<>();
//
//        try {
//            String transformedJson = request.get("transformedData");
//            String templateId = request.get("templateId");
//
//            // Validate inputs
//            if (transformedJson == null || transformedJson.trim().isEmpty()) {
//                response.put("success", false);
//                response.put("message", "transformedData is required");
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            if (templateId == null || templateId.trim().isEmpty()) {
//                response.put("success", false);
//                response.put("message", "templateId is required");
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            log.info("📝 Generating PDF with template: {}", templateId);
//
//            // Generate PDF
//            String outputPath = resumeService.generatePdfFromTemplate(transformedJson, templateId);
//
//            response.put("success", true);
//            response.put("message", "PDF generated successfully");
//            response.put("filePath", outputPath);
//            response.put("downloadUrl", "/api/resume/download?path=" + outputPath);
//            response.put("templateId", templateId);
//
//            log.info("✅ PDF generated: {}", outputPath);
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            log.error("❌ Error generating PDF", e);
//            response.put("success", false);
//            response.put("message", "Error: " + e.getMessage());
//            response.put("error", e.getClass().getSimpleName());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }
//
//    /**
//     * Download generated resume PDF
//     */
//    @GetMapping("/download")
//    public ResponseEntity<Resource> downloadResume(@RequestParam("path") String filePath) {
//        try {
//            log.info("📥 Download request for: {}", filePath);
//
//            File file = new File(filePath);
//
//            if (!file.exists()) {
//                log.error("❌ File not found: {}", filePath);
//                return ResponseEntity.notFound().build();
//            }
//
//            Resource resource = new FileSystemResource(file);
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.add(HttpHeaders.CONTENT_DISPOSITION,
//                    "attachment; filename=" + file.getName());
//            headers.add(HttpHeaders.CONTENT_TYPE, "application/pdf");
//
//            log.info("✅ Serving file: {} ({} bytes)", file.getName(), file.length());
//
//            return ResponseEntity.ok()
//                    .headers(headers)
//                    .contentLength(file.length())
//                    .body(resource);
//
//        } catch (Exception e) {
//            log.error("❌ Error downloading resume", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }
//
//    /**
//     * Health check endpoint
//     */
//    @GetMapping("/health")
//    public ResponseEntity<Map<String, Object>> healthCheck() {
//        Map<String, Object> response = new HashMap<>();
//        response.put("status", "UP");
//        response.put("service", "Gemini Resume Transformation Service");
//        response.put("aiProvider", "Google Gemini");
//        response.put("version", "2.0");
//        response.put("features", List.of(
//                "PDF to JSON extraction",
//                "AI-powered optimization",
//                "Database template storage",
//                "Dynamic PDF generation"
//        ));
//        return ResponseEntity.ok(response);
//    }
//}

package com.inn.automate.REST;

import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.POJO.GeneratedResume;
import com.inn.automate.POJO.TemplateDTO;
import com.inn.automate.POJO.TransformedResume;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/resume")
@Slf4j
@CrossOrigin
public class ResumeController {

    @Autowired
    private ResumeTransformationService resumeService;

    @Autowired
    private JwtFilter jwtFilter;

    /**
     * Step 1: Transform resume and save to database
     * Now stores data in DB instead of returning JSON directly
     */
    @PostMapping(value = "/transform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> transformResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Get current user ID from JWT
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null || currentUser.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get user ID (you'll need to fetch this from UserDAO)
            // For now, using email as identifier, but ideally fetch user.getId()
            String userId = currentUser; // TODO: Fetch actual user ID from database

            // Validate file
            if (file == null || file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please upload a PDF file");
                return ResponseEntity.badRequest().body(response);
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                response.put("success", false);
                response.put("message", "Only PDF files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                response.put("success", false);
                response.put("message", "File size exceeds 10MB limit");
                return ResponseEntity.badRequest().body(response);
            }

            if (jobDescription == null || jobDescription.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Job description is required");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("📤 Processing resume for user: {} - File: {}", userId, file.getOriginalFilename());

            // Transform and save to database
            TransformedResume savedResume = resumeService.transformAndSaveResume(file, jobDescription, userId);

            response.put("success", true);
            response.put("message", "Resume transformed and saved successfully");
            response.put("resumeId", savedResume.getResumeId());
            response.put("userId", savedResume.getUserId());
            response.put("version", savedResume.getVersion());
            response.put("createdAt", savedResume.getCreatedAt().toString());
            response.put("nextStep", "GET /api/resume/templates to view available templates");
            response.put("finalStep", "POST /api/resume/generate-pdf with resumeId and templateId");

            log.info("✅ Resume saved with ID: {}", savedResume.getResumeId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error transforming resume", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get user's transformed resumes
     */
    @GetMapping("/my-resumes")
    public ResponseEntity<Map<String, Object>> getMyResumes() {
        Map<String, Object> response = new HashMap<>();

        try {
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            List<TransformedResume> resumes = resumeService.getUserResumes(currentUser);

            // Convert to simple DTOs
            List<Map<String, Object>> resumeList = resumes.stream()
                    .map(r -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("resumeId", r.getResumeId());
                        dto.put("originalFilename", r.getOriginalFilename());
                        dto.put("jobDescription", r.getJobDescription());
                        dto.put("version", r.getVersion());
                        dto.put("createdAt", r.getCreatedAt().toString());
                        return dto;
                    })
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("count", resumeList.size());
            response.put("resumes", resumeList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching resumes", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Step 2: Get available templates
     */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> getAvailableTemplates() {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("📋 Fetching templates...");

            List<TemplateDTO> templates = resumeService.getAvailableTemplates();

            response.put("success", true);
            response.put("message", "Templates retrieved successfully");
            response.put("count", templates.size());
            response.put("templates", templates);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error retrieving templates", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Step 3: Generate PDF from saved resume
     * New format: { "userId": "U000001", "resumeId": 5, "templateId": "1" }
     */
    @PostMapping(value = "/generate-pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> generatePdf(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Get current authenticated user
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Extract request parameters
            String requestedUserId = (String) request.get("userId");
            Long resumeId = null;
            String templateId = null;

            // Parse resumeId (can be Integer or Long from JSON)
            Object resumeIdObj = request.get("resumeId");
            if (resumeIdObj instanceof Integer) {
                resumeId = ((Integer) resumeIdObj).longValue();
            } else if (resumeIdObj instanceof Long) {
                resumeId = (Long) resumeIdObj;
            } else if (resumeIdObj instanceof String) {
                resumeId = Long.parseLong((String) resumeIdObj);
            }

            templateId = String.valueOf(request.get("templateId"));

            // Validate inputs
            if (requestedUserId == null || requestedUserId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "userId is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (resumeId == null) {
                response.put("success", false);
                response.put("message", "resumeId is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (templateId == null || templateId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "templateId is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Security check: Ensure user can only access their own resumes
            if (!currentUser.equals(requestedUserId)) {
                response.put("success", false);
                response.put("message", "Access denied: You can only generate PDFs for your own resumes");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            log.info("📝 Generating PDF - User: {}, Resume: {}, Template: {}",
                    requestedUserId, resumeId, templateId);

            // Generate PDF from saved resume
            GeneratedResume generatedResume = resumeService.generatePdfFromSavedResume(
                    resumeId, templateId, requestedUserId);

            response.put("success", true);
            response.put("message", "PDF generated successfully");
            response.put("generatedId", generatedResume.getGeneratedId());
            response.put("resumeId", generatedResume.getResumeId());
            response.put("templateId", generatedResume.getTemplateId());
            response.put("filePath", generatedResume.getPdfFilePath());
            response.put("fileSize", generatedResume.getFileSize());
            response.put("downloadUrl", "/api/resume/download/" + generatedResume.getGeneratedId());
            response.put("createdAt", generatedResume.getCreatedAt().toString());

            log.info("✅ PDF generated: {}", generatedResume.getPdfFilePath());
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Business logic error", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("❌ Error generating PDF", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get user's generated PDFs
     */
    @GetMapping("/my-generated")
    public ResponseEntity<Map<String, Object>> getMyGeneratedResumes() {
        Map<String, Object> response = new HashMap<>();

        try {
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            List<GeneratedResume> generated = resumeService.getUserGeneratedResumes(currentUser);

            List<Map<String, Object>> generatedList = generated.stream()
                    .map(g -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("generatedId", g.getGeneratedId());
                        dto.put("resumeId", g.getResumeId());
                        dto.put("templateId", g.getTemplateId());
                        dto.put("fileSize", g.getFileSize());
                        dto.put("downloadCount", g.getDownloadCount());
                        dto.put("createdAt", g.getCreatedAt().toString());
                        dto.put("downloadUrl", "/api/resume/download/" + g.getGeneratedId());
                        return dto;
                    })
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("count", generatedList.size());
            response.put("generated", generatedList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching generated resumes", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download generated PDF by generatedId
     */
    @GetMapping("/download/{generatedId}")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long generatedId) {
        try {
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            log.info("📥 Download request - Generated ID: {}, User: {}", generatedId, currentUser);

            // Track download
            resumeService.trackDownload(generatedId, currentUser);

            // Get generated resume info
            List<GeneratedResume> allGenerated = resumeService.getUserGeneratedResumes(currentUser);
            Optional<GeneratedResume> optionalGenerated = allGenerated.stream()
                    .filter(g -> g.getGeneratedId().equals(generatedId))
                    .findFirst();

            if (optionalGenerated.isEmpty()) {
                log.error("❌ Generated resume not found or access denied");
                return ResponseEntity.notFound().build();
            }

            GeneratedResume generated = optionalGenerated.get();
            File file = new File(generated.getPdfFilePath());

            if (!file.exists()) {
                log.error("❌ File not found: {}", generated.getPdfFilePath());
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=" + file.getName());
            headers.add(HttpHeaders.CONTENT_TYPE, "application/pdf");

            log.info("✅ Serving file: {} ({} bytes)", file.getName(), file.length());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            log.error("❌ Error downloading resume", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Gemini Resume Transformation Service");
        response.put("version", "3.0");
        response.put("features", List.of(
                "User-based resume management",
                "Database storage",
                "PDF generation tracking",
                "Download analytics"
        ));
        return ResponseEntity.ok(response);
    }
}