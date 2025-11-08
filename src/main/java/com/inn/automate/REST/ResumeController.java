//    package com.inn.automate.REST;
//
//
//    import com.inn.automate.Service.ResumeTransformationService;
//    import lombok.extern.slf4j.Slf4j;
//    import org.springframework.beans.factory.annotation.Autowired;
//    import org.springframework.core.io.FileSystemResource;
//    import org.springframework.core.io.Resource;
//    import org.springframework.http.HttpHeaders;
//    import org.springframework.http.HttpStatus;
//    import org.springframework.http.MediaType;
//    import org.springframework.http.ResponseEntity;
//    import org.springframework.web.bind.annotation.*;
//    import org.springframework.web.multipart.MultipartFile;
//
//    import java.io.File;
//    import java.util.HashMap;
//    import java.util.Map;
//
//    @RestController
//    @RequestMapping("/api/resume")
//    @Slf4j
//    @CrossOrigin
//    public class ResumeController {
//
//        @Autowired
//        private ResumeTransformationService ResumeService;
//
//        /**
//         * Transform resume using Gemini AI
//         *
//         * @param file - Resume PDF file
//         * @param jobDescription - Target job description
//         * @return Transformed resume file path
//         */
//        @PostMapping(value = "/transform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//        public ResponseEntity<Map<String, Object>> transformResume(
//                @RequestParam("file") MultipartFile file,
//                @RequestParam("jobDescription") String jobDescription) {
//
//            Map<String, Object> response = new HashMap<>();
//
//            try {
//                // Validate file
//                if (file.isEmpty()) {
//                    response.put("message", "Please upload a PDF file");
//                    return ResponseEntity.badRequest().body(response);
//                }
//
//                // Check if file is PDF
//                String contentType = file.getContentType();
//                if (contentType == null || !contentType.equals("application/pdf")) {
//                    response.put("message", "Only PDF files are allowed");
//                    return ResponseEntity.badRequest().body(response);
//                }
//
//                // Validate file size (10MB limit)
//                if (file.getSize() > 10 * 1024 * 1024) {
//                    response.put("message", "File size exceeds 10MB limit");
//                    return ResponseEntity.badRequest().body(response);
//                }
//
//                // Process resume with Gemini
//                log.info("Processing resume with Gemini: {}", file.getOriginalFilename());
//                String outputPath = ResumeService.transformResume(file, jobDescription);
//
//                response.put("message", "Resume transformed successfully using Gemini AI");
//                response.put("filePath", outputPath);
//                response.put("downloadUrl", "/api/resume/gemini/download?path=" + outputPath);
//                response.put("aiModel", "Gemini 1.5 Pro");
//
//                return ResponseEntity.ok(response);
//
//            } catch (Exception e) {
//                log.error("Error transforming resume with Gemini", e);
//                response.put("message", "Error: " + e.getMessage());
//                response.put("error", e.getClass().getSimpleName());
//                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//            }
//        }
//
//        /**
//         * Download transformed resume
//         */
//        @GetMapping("/download")
//        public ResponseEntity<Resource> downloadResume(@RequestParam("path") String filePath) {
//            try {
//                File file = new File(filePath);
//
//                if (!file.exists()) {
//                    return ResponseEntity.notFound().build();
//                }
//
//                Resource resource = new FileSystemResource(file);
//
//                HttpHeaders headers = new HttpHeaders();
//                headers.add(HttpHeaders.CONTENT_DISPOSITION,
//                        "attachment; filename=" + file.getName());
//                headers.add(HttpHeaders.CONTENT_TYPE, "application/pdf");
//
//                return ResponseEntity.ok()
//                        .headers(headers)
//                        .contentLength(file.length())
//                        .body(resource);
//
//            } catch (Exception e) {
//                log.error("Error downloading resume", e);
//                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//            }
//        }
//
//        /**
//         * Health check endpoint
//         */
//        @GetMapping("/health")
//        public ResponseEntity<Map<String, String>> healthCheck() {
//            Map<String, String> response = new HashMap<>();
//            response.put("status", "UP");
//            response.put("service", "Gemini Resume Transformation Service");
//            response.put("aiProvider", "Google Gemini");
//            return ResponseEntity.ok(response);
//        }
//    }

package com.inn.automate.REST;

import com.inn.automate.POJO.TemplateDTO;
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

@RestController
@RequestMapping("/api/resume")
@Slf4j
@CrossOrigin
public class ResumeController {

    @Autowired
    private ResumeTransformationService resumeService;

    /**
     * Step 1: Extract and transform resume to JSON
     * Returns structured JSON without generating PDF
     */
    @PostMapping(value = "/transform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> transformResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                response.put("message", "Please upload a PDF file");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if file is PDF
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                response.put("message", "Only PDF files are allowed. Received: " + contentType);
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file size (10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                response.put("message", "File size exceeds 10MB limit");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate job description
            if (jobDescription == null || jobDescription.trim().isEmpty()) {
                response.put("message", "Job description is required");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("📤 Processing resume: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

            // Transform resume to JSON
            String transformedJson = resumeService.transformResume(file, jobDescription);

            response.put("success", true);
            response.put("message", "Resume extracted and transformed successfully");
            response.put("transformedData", transformedJson); // JSON string
            response.put("nextStep", "GET /api/resume/templates to view available templates");
            response.put("finalStep", "POST /api/resume/generate-pdf with transformedData and templateId");

            log.info("✅ Resume transformation complete");
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
     * Step 2: Get available templates from database
     */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> getAvailableTemplates() {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("📋 Fetching available templates...");

            List<TemplateDTO> templates = resumeService.getAvailableTemplates();

            response.put("success", true);
            response.put("message", "Templates retrieved successfully");
            response.put("count", templates.size());
            response.put("templates", templates);

            log.info("✅ Retrieved {} templates", templates.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error retrieving templates", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Step 3: Generate PDF with selected template
     */
    @PostMapping(value = "/generate-pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> generatePdf(
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            String transformedJson = request.get("transformedData");
            String templateId = request.get("templateId");

            // Validate inputs
            if (transformedJson == null || transformedJson.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "transformedData is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (templateId == null || templateId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "templateId is required");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("📝 Generating PDF with template: {}", templateId);

            // Generate PDF
            String outputPath = resumeService.generatePdfFromTemplate(transformedJson, templateId);

            response.put("success", true);
            response.put("message", "PDF generated successfully");
            response.put("filePath", outputPath);
            response.put("downloadUrl", "/api/resume/download?path=" + outputPath);
            response.put("templateId", templateId);

            log.info("✅ PDF generated: {}", outputPath);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error generating PDF", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download generated resume PDF
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadResume(@RequestParam("path") String filePath) {
        try {
            log.info("📥 Download request for: {}", filePath);

            File file = new File(filePath);

            if (!file.exists()) {
                log.error("❌ File not found: {}", filePath);
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
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Gemini Resume Transformation Service");
        response.put("aiProvider", "Google Gemini");
        response.put("version", "2.0");
        response.put("features", List.of(
                "PDF to JSON extraction",
                "AI-powered optimization",
                "Database template storage",
                "Dynamic PDF generation"
        ));
        return ResponseEntity.ok(response);
    }
}