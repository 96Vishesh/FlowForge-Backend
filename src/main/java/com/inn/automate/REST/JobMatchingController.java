package com.inn.automate.REST;

import com.inn.automate.Service.JobMatchingService;
import com.inn.automate.POJO.JobMatchRequest;
import com.inn.automate.POJO.JobMatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@Slf4j
@CrossOrigin
public class JobMatchingController {

    @Autowired
    private JobMatchingService jobMatchingService;

    /**
     * Match resume against company job listings
     *
     * @param file - Resume PDF file
     * @param jobsJson - JSON string containing company jobs data
     * @return List of matching jobs with compatibility scores > 70%
     */
    @PostMapping(value = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobMatchResponse> matchJobs(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobsData") String jobsJson) {

        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(JobMatchResponse.error("Please upload a PDF file"));
            }

            // Check if file is PDF
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                return ResponseEntity.badRequest()
                        .body(JobMatchResponse.error("Only PDF files are allowed"));
            }

            // Validate file size (10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(JobMatchResponse.error("File size exceeds 10MB limit"));
            }

            // Validate JSON
            if (jobsJson == null || jobsJson.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(JobMatchResponse.error("Jobs data is required"));
            }

            log.info("Processing resume: {} with {} jobs data",
                    file.getOriginalFilename(), jobsJson.length());

            // Process matching
            JobMatchResponse response = jobMatchingService.matchResumeWithJobs(file, jobsJson);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error matching jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(JobMatchResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * Match resume with POST body JSON (alternative endpoint)
     */
    @PostMapping(value = "/match-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobMatchResponse> matchJobsWithBody(
            @RequestParam("file") MultipartFile file,
            @RequestPart("jobsData") JobMatchRequest jobsData) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(JobMatchResponse.error("Please upload a PDF file"));
            }

            log.info("Processing resume with {} companies", jobsData.getCompanies().size());

            JobMatchResponse response = jobMatchingService.matchResumeWithJobs(file, jobsData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error matching jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(JobMatchResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Job Matching Service");
        response.put("aiProvider", "Google Gemini");
        return ResponseEntity.ok(response);
    }
}