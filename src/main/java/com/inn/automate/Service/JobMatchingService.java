package com.inn.automate.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inn.automate.POJO.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobMatchingService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Autowired
    private ResumeTransformationService resumeService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final double MATCH_THRESHOLD = 70.0;

    /**
     * Main method to match resume with jobs
     */
    public JobMatchResponse matchResumeWithJobs(MultipartFile pdfFile, String jobsJson) throws Exception {
        // Parse jobs data
        JobMatchRequest jobsData = objectMapper.readValue(jobsJson, JobMatchRequest.class);
        return matchResumeWithJobs(pdfFile, jobsData);
    }

    public JobMatchResponse matchResumeWithJobs(MultipartFile pdfFile, JobMatchRequest jobsData) throws Exception {
        log.info("🔍 Starting job matching process...");

        // Step 1: Extract and parse resume
        log.info("📄 Extracting resume text...");
        String resumeText = extractPdfText(pdfFile);
        ResumeProfile resumeProfile = parseResumeProfile(resumeText);
        log.info("✅ Resume parsed: {} skills, {} years experience",
                resumeProfile.getSkills().size(), resumeProfile.getExperienceYears());

        // Step 2: Match against all jobs
        log.info("🎯 Matching against {} companies...", jobsData.getCompanies().size());
        List<JobMatch> matches = new ArrayList<>();

        for (CompanyJobs company : jobsData.getCompanies()) {
            for (JobListing job : company.getJobs()) {
                JobMatch match = calculateJobMatch(resumeProfile, company, job);
                if (match.getCompatibilityScore() >= MATCH_THRESHOLD) {
                    matches.add(match);
                }
            }
        }

        // Step 3: Sort by compatibility score
        matches.sort((a, b) -> Double.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));

        log.info("✅ Found {} matching jobs above {}%", matches.size(), MATCH_THRESHOLD);

        return JobMatchResponse.success(matches, resumeProfile);
    }

    /**
     * Extract text from PDF
     */
    private String extractPdfText(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Parse resume into structured profile
     */
    private ResumeProfile parseResumeProfile(String resumeText) {
        ResumeProfile profile = new ResumeProfile();

        String lowerText = resumeText.toLowerCase();

        // Extract skills using common patterns
        profile.setSkills(extractSkills(resumeText));

        // Extract experience years
        profile.setExperienceYears(estimateExperienceYears(resumeText));

        // Extract education
        profile.setEducation(extractEducation(resumeText));

        // Extract certifications
        profile.setCertifications(extractCertifications(resumeText));

        // Store full text for AI analysis
        profile.setFullText(resumeText);

        return profile;
    }

    /**
     * Extract skills from resume text
     */
    private Set<String> extractSkills(String text) {
        Set<String> skills = new HashSet<>();
        String lowerText = text.toLowerCase();

        // Common tech skills to search for
        String[] commonSkills = {
                "java", "python", "javascript", "react", "angular", "vue", "node.js", "spring boot",
                "docker", "kubernetes", "aws", "azure", "gcp", "sql", "mongodb", "postgresql",
                "git", "jenkins", "ci/cd", "microservices", "rest api", "graphql",
                "machine learning", "ai", "data science", "tensorflow", "pytorch",
                "html", "css", "typescript", "c++", "c#", "ruby", "php", "go", "rust",
                "agile", "scrum", "jira", "project management", "leadership", "communication"
        };

        for (String skill : commonSkills) {
            if (lowerText.contains(skill)) {
                skills.add(skill);
            }
        }

        return skills;
    }

    /**
     * Estimate years of experience
     */
    private int estimateExperienceYears(String text) {
        // Look for patterns like "5 years", "5+ years", "2019-2023"
        Pattern yearsPattern = Pattern.compile("(\\d+)\\s*\\+?\\s*years?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = yearsPattern.matcher(text);

        int maxYears = 0;
        while (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            maxYears = Math.max(maxYears, years);
        }

        // Also check date ranges
        Pattern datePattern = Pattern.compile("(20\\d{2})\\s*-\\s*(20\\d{2}|present)", Pattern.CASE_INSENSITIVE);
        Matcher dateMatcher = datePattern.matcher(text);

        while (dateMatcher.find()) {
            int startYear = Integer.parseInt(dateMatcher.group(1));
            int endYear = dateMatcher.group(2).toLowerCase().contains("present") ?
                    2024 : Integer.parseInt(dateMatcher.group(2));
            maxYears = Math.max(maxYears, endYear - startYear);
        }

        return maxYears;
    }

    /**
     * Extract education information
     */
    private List<String> extractEducation(String text) {
        List<String> education = new ArrayList<>();
        String lowerText = text.toLowerCase();

        String[] degrees = {"bachelor", "master", "phd", "b.tech", "m.tech", "mba", "b.s.", "m.s."};

        for (String degree : degrees) {
            if (lowerText.contains(degree)) {
                education.add(degree);
            }
        }

        return education;
    }

    /**
     * Extract certifications
     */
    private List<String> extractCertifications(String text) {
        List<String> certs = new ArrayList<>();
        String lowerText = text.toLowerCase();

        String[] commonCerts = {
                "aws certified", "azure certified", "gcp certified", "pmp", "scrum master",
                "oracle certified", "cisco", "comptia", "certified kubernetes"
        };

        for (String cert : commonCerts) {
            if (lowerText.contains(cert)) {
                certs.add(cert);
            }
        }

        return certs;
    }

    /**
     * Calculate match between resume and job using AI + heuristics
     */
    private JobMatch calculateJobMatch(ResumeProfile resume, CompanyJobs company, JobListing job) throws Exception {
        JobMatch match = new JobMatch();

        // Use job's company field if available, otherwise use company object
        String companyName = (job.getCompany() != null && !job.getCompany().isEmpty())
                ? job.getCompany()
                : company.getCompanyName();

        match.setCompanyName(companyName);
        match.setJobTitle(job.getJobTitle());
        match.setJobProfile(job.getProfile());
        match.setJobDescription(job.getDescription());
        match.setRequiredSkills(job.getRequiredSkills());
        match.setExperienceRequired(job.getExperienceRequired());
        match.setLocation(job.getLocation());

        // Calculate component scores
        double skillsScore = calculateSkillsMatch(resume.getSkills(), job.getRequiredSkills());
        double experienceScore = calculateExperienceMatch(resume.getExperienceYears(), job.getExperienceRequired());
        double aiScore = calculateAIMatch(resume, job);

        // Weighted average
        double finalScore = (skillsScore * 0.5) + (experienceScore * 0.2) + (aiScore * 0.3);

        match.setCompatibilityScore(Math.round(finalScore * 100.0) / 100.0);
        match.setMatchedSkills(getMatchedSkills(resume.getSkills(), job.getRequiredSkills()));
        match.setMatchReason(generateMatchReason(skillsScore, experienceScore, aiScore));

        return match;
    }

    /**
     * Calculate skills match percentage
     */
    private double calculateSkillsMatch(Set<String> resumeSkills, List<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return 50.0; // Neutral score if no skills specified
        }

        Set<String> lowerResumeSkills = resumeSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        long matchedCount = requiredSkills.stream()
                .map(String::toLowerCase)
                .filter(lowerResumeSkills::contains)
                .count();

        return (matchedCount * 100.0) / requiredSkills.size();
    }

    /**
     * Calculate experience match
     */
    private double calculateExperienceMatch(int resumeYears, String requiredExperience) {
        if (requiredExperience == null || requiredExperience.isEmpty()) {
            return 80.0; // Good score if no experience specified
        }

        // Extract years from requirement (e.g., "3-5 years", "5+ years")
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(requiredExperience);

        if (matcher.find()) {
            int requiredYears = Integer.parseInt(matcher.group(1));

            if (resumeYears >= requiredYears) {
                return 100.0;
            } else if (resumeYears >= requiredYears * 0.75) {
                return 80.0;
            } else if (resumeYears >= requiredYears * 0.5) {
                return 60.0;
            } else {
                return 40.0;
            }
        }

        return 70.0; // Default if can't parse
    }

    /**
     * Use AI to analyze deeper match
     */
    private double calculateAIMatch(ResumeProfile resume, JobListing job) {
        try {
            String prompt = String.format("""
                Analyze compatibility between resume and job. Return ONLY a number 0-100.
                
                Resume Skills: %s
                Resume Experience: %d years
                
                Job: %s
                Required Skills: %s
                Required Experience: %s
                
                Return only compatibility score (0-100):
                """,
                    String.join(", ", resume.getSkills()),
                    resume.getExperienceYears(),
                    job.getJobTitle(),
                    job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "Not specified",
                    job.getExperienceRequired() != null ? job.getExperienceRequired() : "Not specified"
            );

            String response = callGeminiSimple(prompt);

            // Extract number from response
            Pattern pattern = Pattern.compile("\\d+");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group());
            }

        } catch (Exception e) {
            log.warn("AI matching failed, using heuristic: {}", e.getMessage());
        }

        return 70.0; // Fallback score
    }

    /**
     * Simple Gemini API call for scoring
     */
    private String callGeminiSimple(String prompt) throws Exception {
        var contentsArray = new ArrayList<Map<String, Object>>();
        var partsArray = new ArrayList<Map<String, String>>();
        partsArray.add(Map.of("text", prompt));
        contentsArray.add(Map.of("parts", partsArray));

        var requestMap = new HashMap<String, Object>();
        requestMap.put("contents", contentsArray);

        var generationConfig = new HashMap<String, Object>();
        generationConfig.put("temperature", 0.3);
        generationConfig.put("maxOutputTokens", 100);
        requestMap.put("generationConfig", generationConfig);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=" + geminiApiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestMap)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API failed");
        }

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        return jsonResponse.get("candidates").get(0)
                .get("content").get("parts").get(0)
                .get("text").asText();
    }

    /**
     * Get list of matched skills
     */
    private List<String> getMatchedSkills(Set<String> resumeSkills, List<String> requiredSkills) {
        if (requiredSkills == null) return new ArrayList<>();

        Set<String> lowerResumeSkills = resumeSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return requiredSkills.stream()
                .filter(skill -> lowerResumeSkills.contains(skill.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Generate human-readable match reason
     */
    private String generateMatchReason(double skillsScore, double experienceScore, double aiScore) {
        List<String> reasons = new ArrayList<>();

        if (skillsScore >= 80) {
            reasons.add("Strong skills match");
        } else if (skillsScore >= 60) {
            reasons.add("Good skills alignment");
        }

        if (experienceScore >= 90) {
            reasons.add("Experience exceeds requirements");
        } else if (experienceScore >= 70) {
            reasons.add("Meets experience requirements");
        }

        if (aiScore >= 80) {
            reasons.add("AI analysis shows excellent fit");
        }

        return reasons.isEmpty() ? "Potential match" : String.join(", ", reasons);
    }
}