//
//package com.inn.automate.Service;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.inn.automate.DAO.TemplateRepository;
//import com.inn.automate.DAO.TransformedResumeRepository;
//import com.inn.automate.DAO.GeneratedResumeRepository;
//import com.inn.automate.JWT.JwtFilter;
//import com.inn.automate.POJO.ResumeData;
//import com.inn.automate.POJO.Template;
//import com.inn.automate.POJO.TemplateDTO;
//import com.inn.automate.POJO.TransformedResume;
//import com.inn.automate.POJO.GeneratedResume;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.text.PDFTextStripper;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import javax.annotation.PostConstruct;
//import java.io.*;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//
//@Service
//@Slf4j
//public class ResumeTransformationService {
//
//    @Autowired
//    private TemplateRepository templateRepository;
//
//    @Autowired
//    private TransformedResumeRepository transformedResumeRepository;
//
//    @Autowired
//    private GeneratedResumeRepository generatedResumeRepository;
//
//    @Autowired
//    private JwtFilter jwtFilter;
//
//    @Value("${gemini.api.key}")
//    private String geminiApiKey;
//
//    @Value("${resume.output.directory:resumes/generated}")
//    private String outputDirectory;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//    private final HttpClient httpClient = HttpClient.newBuilder()
//            .connectTimeout(Duration.ofSeconds(30))
//            .build();
//
//    private String workingModel = null;
//    private String workingApiBase = null;
//
//    @PostConstruct
//    public void detectAvailableModel() {
//        log.info("🔍 Detecting available Gemini models...");
//        String[][] modelCombinations = {
//                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-2.5-flash-preview-05-20"},
//                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-2.5-flash"},
//                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-2.0-flash-exp"},
//                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-pro-latest"},
//                {"https://generativelanguage.googleapis.com/v1/models/", "gemini-pro"}
//        };
//
//        for (String[] combo : modelCombinations) {
//            if (testModel(combo[0], combo[1])) {
//                workingModel = combo[1];
//                workingApiBase = combo[0];
//                log.info("✅ SUCCESS! Using model: {} with API: {}", combo[1], combo[0]);
//                return;
//            }
//        }
//        log.error("❌ No working Gemini model found!");
//    }
//
//    private boolean testModel(String apiBase, String model) {
//        try {
//            var requestMap = new java.util.HashMap<String, Object>();
//            var contentsArray = new ArrayList<java.util.Map<String, Object>>();
//            var partsArray = new ArrayList<java.util.Map<String, String>>();
//            partsArray.add(java.util.Map.of("text", "test"));
//            contentsArray.add(java.util.Map.of("parts", partsArray));
//            requestMap.put("contents", contentsArray);
//
//            String url = apiBase + model + ":generateContent?key=" + geminiApiKey;
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(url))
//                    .header("Content-Type", "application/json")
//                    .timeout(Duration.ofSeconds(10))
//                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestMap)))
//                    .build();
//
//            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//            return response.statusCode() == 200 && objectMapper.readTree(response.body()).has("candidates");
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    /**
//     * Transform resume and save to database
//     */
//    public TransformedResume transformAndSaveResume(MultipartFile pdfFile, String jobDescription, String userId) throws Exception {
//        if (workingModel == null || workingApiBase == null) {
//            throw new RuntimeException("No working Gemini model available!");
//        }
//
//        log.info("🚀 Starting resume transformation for user: {}", userId);
//
//        // Extract and transform
//        String resumeText = parsePdfToText(pdfFile);
//        ResumeSections sections = parseResumeSection(resumeText);
//        String resumeJson = convertSectionsToJson(sections);
//        String transformedJson = transformResumeOptimized(resumeJson, jobDescription);
//
//        // Get next version number
//        Integer latestVersion = transformedResumeRepository.getLatestVersionForUser(userId);
//        int nextVersion = (latestVersion == null) ? 1 : latestVersion + 1;
//
//        // Save to database
//        TransformedResume resume = new TransformedResume();
//        resume.setUserId(userId);
//        resume.setOriginalFilename(pdfFile.getOriginalFilename());
//        resume.setJobDescription(jobDescription);
//        resume.setTransformedData(transformedJson);
//        resume.setVersion(nextVersion);
//        resume.setIsActive(true);
//
//        TransformedResume savedResume = transformedResumeRepository.save(resume);
//        log.info("✅ Resume saved to database with ID: {}", savedResume.getResumeId());
//
//        return savedResume;
//    }
//
//    /**
//     * Get user's transformed resumes
//     */
//    public List<TransformedResume> getUserResumes(String userId) {
//        return transformedResumeRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
//    }
//
//    /**
//     * Get specific resume by ID (with user verification)
//     */
//    public Optional<TransformedResume> getResumeById(Long resumeId, String userId) {
//        return transformedResumeRepository.findByResumeIdAndUserId(resumeId, userId);
//    }
//
//    /**
//     * Generate PDF from saved resume data
//     */
//    public GeneratedResume generatePdfFromSavedResume(Long resumeId, String templateId, String userId) throws Exception {
//        log.info("📝 Generating PDF for resume ID: {} with template: {}", resumeId, templateId);
//
//        // Fetch the transformed resume
//        Optional<TransformedResume> optionalResume = transformedResumeRepository.findByResumeIdAndUserId(resumeId, userId);
//
//        if (optionalResume.isEmpty()) {
//            throw new RuntimeException("Resume not found or access denied");
//        }
//
//        TransformedResume transformedResume = optionalResume.get();
//        String transformedJson = transformedResume.getTransformedData();
//
//        // Fetch template
//        Template template = fetchTemplate(templateId);
//        if (template == null) {
//            log.warn("⚠️ Template not found: {}, using default", templateId);
//            template = getDefaultTemplate();
//        }
//
//        ResumeData resumeData = objectMapper.readValue(transformedJson, ResumeData.class);
//
//        // Create output directory
//        Path outputDir = Paths.get(outputDirectory);
//        if (!Files.exists(outputDir)) {
//            Files.createDirectories(outputDir);
//        }
//
//        // Generate filename
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String filename = String.format("resume_%s_%s_%s.pdf", userId, templateId, timestamp);
//        Path outputPath = outputDir.resolve(filename);
//
//        // Generate PDF
//        String finalHtml = template.getHtmlCode() != null ?
//                populateTemplate(template.getHtmlCode(), resumeData) :
//                generateHtmlFromJson(resumeData);
//
//        convertHtmlToPdf(finalHtml, outputPath.toString());
//
//        // Get file size
//        File pdfFile = new File(outputPath.toString());
//        long fileSize = pdfFile.length();
//
//        // Save generation record
//        GeneratedResume generatedResume = new GeneratedResume();
//        generatedResume.setUserId(userId);
//        generatedResume.setResumeId(resumeId);
//        generatedResume.setTemplateId(Long.parseLong(templateId));
//        generatedResume.setPdfFilePath(outputPath.toString());
//        generatedResume.setFileSize(fileSize);
//        generatedResume.setDownloadCount(0);
//
//        GeneratedResume savedGenerated = generatedResumeRepository.save(generatedResume);
//        log.info("✅ PDF generated and saved: {}", outputPath);
//
//        return savedGenerated;
//    }
//
//    /**
//     * Track PDF downloads
//     */
//    public void trackDownload(Long generatedId, String userId) {
//        Optional<GeneratedResume> optional = generatedResumeRepository.findByGeneratedIdAndUserId(generatedId, userId);
//
//        if (optional.isPresent()) {
//            GeneratedResume generated = optional.get();
//            generated.setDownloadCount(generated.getDownloadCount() + 1);
//            generated.setLastDownloadedAt(LocalDateTime.now());
//            generatedResumeRepository.save(generated);
//        }
//    }
//
//    /**
//     * Get user's generated resumes
//     */
//    public List<GeneratedResume> getUserGeneratedResumes(String userId) {
//        return generatedResumeRepository.findByUserIdOrderByCreatedAtDesc(userId);
//    }
//
//    /**
//     * Get templates
//     */
//    public List<TemplateDTO> getAvailableTemplates() {
//        log.info("📋 Fetching available templates...");
//
//        List<Template> templates = templateRepository.findByIsActiveTrue();
//
//        if (templates.isEmpty()) {
//            return getDefaultTemplates();
//        }
//
//        return templates.stream()
//                .map(TemplateDTO::fromEntity)
//                .collect(Collectors.toList());
//    }
//
//    private List<TemplateDTO> getDefaultTemplates() {
//        List<TemplateDTO> templates = new ArrayList<>();
//        templates.add(new TemplateDTO(1L, "Modern Professional", "Clean, modern design", null, true));
//        templates.add(new TemplateDTO(2L, "Classic Two-Column", "Traditional layout", null, true));
//        templates.add(new TemplateDTO(3L, "Minimalist", "Minimal design", null, true));
//        return templates;
//    }
//
//    private Template fetchTemplate(String templateId) {
//        try {
//            Long id = Long.parseLong(templateId);
//            return templateRepository.findByTemplateIdAndIsActiveTrue(id).orElse(null);
//        } catch (NumberFormatException e) {
//            return templateRepository.findByName(templateId).orElse(null);
//        }
//    }
//
//    private Template getDefaultTemplate() {
//        Template defaultTemplate = new Template();
//        defaultTemplate.setTemplateId(0L);
//        defaultTemplate.setName("Default");
//        defaultTemplate.setHtmlCode(null);
//        return defaultTemplate;
//    }
//
//    private String populateTemplate(String templateHtml, ResumeData data) {
//        String result = templateHtml;
//
//        if (data.getPersonalInfo() != null) {
//            result = result.replace("{{name}}", escapeHtml(data.getPersonalInfo().getName()));
//            result = result.replace("{{email}}", escapeHtml(data.getPersonalInfo().getEmail()));
//            result = result.replace("{{phone}}", escapeHtml(data.getPersonalInfo().getPhone()));
//            result = result.replace("{{location}}", escapeHtml(data.getPersonalInfo().getLocation()));
//            result = result.replace("{{linkedin}}", escapeHtml(data.getPersonalInfo().getLinkedin()));
//        }
//
//        result = result.replace("{{summary}}", data.getSummary() != null ? escapeHtml(data.getSummary()) : "");
//
//        if (data.getExperience() != null && !data.getExperience().isEmpty()) {
//            StringBuilder experienceHtml = new StringBuilder();
//            for (ResumeData.Experience exp : data.getExperience()) {
//                experienceHtml.append("<div class=\"experience-item\">");
//                experienceHtml.append("<div class=\"job-title\">").append(escapeHtml(exp.getTitle())).append("</div>");
//                experienceHtml.append("<div class=\"company\">").append(escapeHtml(exp.getCompany())).append("</div>");
//                if (exp.getResponsibilities() != null) {
//                    experienceHtml.append("<ul>");
//                    exp.getResponsibilities().forEach(resp ->
//                            experienceHtml.append("<li>").append(escapeHtml(resp)).append("</li>"));
//                    experienceHtml.append("</ul>");
//                }
//                experienceHtml.append("</div>");
//            }
//            result = result.replace("{{experience}}", experienceHtml.toString());
//        }
//
//        if (data.getSkills() != null && data.getSkills().getTechnical() != null) {
//            StringBuilder skillsHtml = new StringBuilder();
//            data.getSkills().getTechnical().forEach(skill ->
//                    skillsHtml.append("<span class=\"skill-tag\">").append(escapeHtml(skill)).append("</span>"));
//            result = result.replace("{{skills}}", skillsHtml.toString());
//        }
//
//        if (data.getEducation() != null && !data.getEducation().isEmpty()) {
//            StringBuilder eduHtml = new StringBuilder();
//            data.getEducation().forEach(edu -> {
//                eduHtml.append("<div class=\"education-item\">");
//                eduHtml.append("<strong>").append(escapeHtml(edu.getDegree())).append("</strong>");
//                eduHtml.append("<div>").append(escapeHtml(edu.getInstitution())).append("</div>");
//                eduHtml.append("</div>");
//            });
//            result = result.replace("{{education}}", eduHtml.toString());
//        }
//
//        return result;
//    }
//
//    // Core processing methods remain the same...
//    private String parsePdfToText(MultipartFile file) throws IOException {
//        try (InputStream inputStream = file.getInputStream();
//             PDDocument document = PDDocument.load(inputStream)) {
//            PDFTextStripper stripper = new PDFTextStripper();
//            return stripper.getText(document);
//        }
//    }
//
//    private ResumeSections parseResumeSection(String text) {
//        // ... (keep existing implementation)
//        ResumeSections sections = new ResumeSections();
//        sections.contactInfo = extractContactInfo(text);
//        // ... rest of the parsing logic
//        return sections;
//    }
//
//    private String extractContactInfo(String text) {
//        // ... (keep existing implementation)
//        return "";
//    }
//
//    private String convertSectionsToJson(ResumeSections sections) throws Exception {
//        // ... (keep existing implementation)
//        return callGemini("prompt", 4000);
//    }
//
//    private String transformResumeOptimized(String resumeJson, String jobDescription) throws Exception {
//        // ... (keep existing implementation)
//        return callGemini("prompt", 4000);
//    }
//
//    private String callGemini(String prompt, int maxTokens) throws Exception {
//        // ... (keep existing implementation)
//        return "";
//    }
//
//    private String cleanJsonResponse(String content) {
//        // ... (keep existing implementation)
//        return content.trim();
//    }
//
//    private String generateHtmlFromJson(ResumeData data) {
//        // ... (keep existing default HTML generation)
//        return "";
//    }
//
//    private String escapeHtml(String text) {
//        if (text == null) return "";
//        return text.replace("&", "&amp;")
//                .replace("<", "&lt;")
//                .replace(">", "&gt;")
//                .replace("\"", "&quot;")
//                .replace("'", "&#39;");
//    }
//
//    private void convertHtmlToPdf(String html, String outputPath) throws Exception {
//        try (OutputStream os = new FileOutputStream(outputPath)) {
//            org.xhtmlrenderer.pdf.ITextRenderer renderer =
//                    new org.xhtmlrenderer.pdf.ITextRenderer();
//            renderer.setDocumentFromString(html);
//            renderer.layout();
//            renderer.createPDF(os);
//        }
//    }
//
//    private static class ResumeSections {
//        String contactInfo = "";
//        java.util.Map<String, String> sections = new java.util.HashMap<>();
//        void addSection(String name, String content) {
//            if (content != null && !content.trim().isEmpty()) {
//                sections.put(name, content.trim());
//            }
//        }
//        int getSectionCount() {
//            return sections.size();
//        }
//    }
//}


package com.inn.automate.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inn.automate.DAO.TemplateRepository;
import com.inn.automate.DAO.TransformedResumeRepository;
import com.inn.automate.DAO.GeneratedResumeRepository;
import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.POJO.ResumeData;
import com.inn.automate.POJO.Template;
import com.inn.automate.POJO.TemplateDTO;
import com.inn.automate.POJO.TransformedResume;
import com.inn.automate.POJO.GeneratedResume;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ResumeTransformationService {

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TransformedResumeRepository transformedResumeRepository;

    @Autowired
    private GeneratedResumeRepository generatedResumeRepository;

    @Autowired
    private JwtFilter jwtFilter;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${resume.output.directory:resumes/generated}")
    private String outputDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private String workingModel = null;
    private String workingApiBase = null;


    @PostConstruct
    public void detectAvailableModel() {
        log.info("🔍 Detecting available Gemini models...");
        String[][] modelCombinations = {
                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-2.5-flash-preview-05-20"},
                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-2.5-flash"},
                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-2.0-flash-exp"},
                {"https://generativelanguage.googleapis.com/v1beta/models/", "gemini-pro-latest"},
                {"https://generativelanguage.googleapis.com/v1/models/", "gemini-pro"}
        };

        for (String[] combo : modelCombinations) {
            if (testModel(combo[0], combo[1])) {
                workingModel = combo[1];
                workingApiBase = combo[0];
                log.info("✅ SUCCESS! Using model: {} with API: {}", combo[1], combo[0]);
                return;
            }
        }
        log.error("❌ No working Gemini model found!");
    }

    private boolean testModel(String apiBase, String model) {
        try {
            // Build request - Java 17 compatible
            Map<String, Object> requestMap = new HashMap<>();
            List<Map<String, Object>> contentsArray = new ArrayList<>();
            List<Map<String, String>> partsArray = new ArrayList<>();

            // Create part map
            Map<String, String> partMap = new HashMap<>();
            partMap.put("text", "test");
            partsArray.add(partMap);

            // Create content map
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", partsArray);
            contentsArray.add(contentMap);

            requestMap.put("contents", contentsArray);

            String url = apiBase + model + ":generateContent?key=" + geminiApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestMap)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && objectMapper.readTree(response.body()).has("candidates");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Transform resume and save to database
     */
    public TransformedResume transformAndSaveResume(MultipartFile pdfFile, String jobDescription, String userId) throws Exception {
        if (workingModel == null || workingApiBase == null) {
            throw new RuntimeException("No working Gemini model available!");
        }

        log.info("🚀 Starting resume transformation for user: {}", userId);

        // Extract and transform
        String resumeText = parsePdfToText(pdfFile);
        ResumeSections sections = parseResumeSection(resumeText);
        String resumeJson = convertSectionsToJson(sections);
        String transformedJson = transformResumeOptimized(resumeJson, jobDescription);

        // Get next version number
        Integer latestVersion = transformedResumeRepository.getLatestVersionForUser(userId);
        int nextVersion = (latestVersion == null) ? 1 : latestVersion + 1;

        // Save to database
        TransformedResume resume = new TransformedResume();
        resume.setUserId(userId);
        resume.setOriginalFilename(pdfFile.getOriginalFilename());
        resume.setJobDescription(jobDescription);
        resume.setTransformedData(transformedJson);
        resume.setVersion(nextVersion);
        resume.setIsActive(true);

        TransformedResume savedResume = transformedResumeRepository.save(resume);
        log.info("✅ Resume saved to database with ID: {}", savedResume.getResumeId());

        return savedResume;
    }

    /**
     * Get user's transformed resumes
     */
    public List<TransformedResume> getUserResumes(String userId) {
        return transformedResumeRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
    }

    /**
     * Get specific resume by ID (with user verification)
     */
    public Optional<TransformedResume> getResumeById(Long resumeId, String userId) {
        return transformedResumeRepository.findByResumeIdAndUserId(resumeId, userId);
    }

    /**
     * Generate PDF from saved resume data
     */
    public GeneratedResume generatePdfFromSavedResume(Long resumeId, String templateId, String userId) throws Exception {
        log.info("📝 Generating PDF for resume ID: {} with template: {}", resumeId, templateId);

        // Fetch the transformed resume
        Optional<TransformedResume> optionalResume = transformedResumeRepository.findByResumeIdAndUserId(resumeId, userId);

        if (optionalResume.isEmpty()) {
            throw new RuntimeException("Resume not found or access denied");
        }

        TransformedResume transformedResume = optionalResume.get();
        String transformedJson = transformedResume.getTransformedData();

        // Fetch template
        Template template = fetchTemplate(templateId);
        if (template == null) {
            log.warn("⚠️ Template not found: {}, using default", templateId);
            template = getDefaultTemplate();
        }

        ResumeData resumeData = objectMapper.readValue(transformedJson, ResumeData.class);

        // Create output directory
        Path outputDir = Paths.get(outputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Generate filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("resume_%s_%s_%s.pdf", userId, templateId, timestamp);
        Path outputPath = outputDir.resolve(filename);

        // Generate PDF
        String finalHtml = template.getHtmlCode() != null ?
                populateTemplate(template.getHtmlCode(), resumeData) :
                generateHtmlFromJson(resumeData);

        convertHtmlToPdf(finalHtml, outputPath.toString());

        // Get file size
        File pdfFile = new File(outputPath.toString());
        long fileSize = pdfFile.length();

        // Save generation record
        GeneratedResume generatedResume = new GeneratedResume();
        generatedResume.setUserId(userId);
        generatedResume.setResumeId(resumeId);
        generatedResume.setTemplateId(Long.parseLong(templateId));
        generatedResume.setPdfFilePath(outputPath.toString());
        generatedResume.setFileSize(fileSize);
        generatedResume.setDownloadCount(0);

        GeneratedResume savedGenerated = generatedResumeRepository.save(generatedResume);
        log.info("✅ PDF generated and saved: {}", outputPath);

        return savedGenerated;
    }

    /**
     * Track PDF downloads
     */
    public void trackDownload(Long generatedId, String userId) {
        Optional<GeneratedResume> optional = generatedResumeRepository.findByGeneratedIdAndUserId(generatedId, userId);

        if (optional.isPresent()) {
            GeneratedResume generated = optional.get();
            generated.setDownloadCount(generated.getDownloadCount() + 1);
            generated.setLastDownloadedAt(LocalDateTime.now());
            generatedResumeRepository.save(generated);
        }
    }

    /**
     * Get user's generated resumes
     */
    public List<GeneratedResume> getUserGeneratedResumes(String userId) {
        return generatedResumeRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get templates
     */
    public List<TemplateDTO> getAvailableTemplates() {
        log.info("📋 Fetching available templates...");

        List<Template> templates = templateRepository.findByIsActiveTrue();

        if (templates.isEmpty()) {
            return getDefaultTemplates();
        }

        return templates.stream()
                .map(TemplateDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private List<TemplateDTO> getDefaultTemplates() {
        List<TemplateDTO> templates = new ArrayList<>();
        templates.add(new TemplateDTO(1L, "Modern Professional", "Clean, modern design", null, true));
        templates.add(new TemplateDTO(2L, "Classic Two-Column", "Traditional layout", null, true));
        templates.add(new TemplateDTO(3L, "Minimalist", "Minimal design", null, true));
        return templates;
    }

    private Template fetchTemplate(String templateId) {
        try {
            Long id = Long.parseLong(templateId);
            return templateRepository.findByTemplateIdAndIsActiveTrue(id).orElse(null);
        } catch (NumberFormatException e) {
            return templateRepository.findByName(templateId).orElse(null);
        }
    }

    private Template getDefaultTemplate() {
        Template defaultTemplate = new Template();
        defaultTemplate.setTemplateId(0L);
        defaultTemplate.setName("Default");
        defaultTemplate.setHtmlCode(null);
        return defaultTemplate;
    }

    private String populateTemplate(String templateHtml, ResumeData data) {
        String result = templateHtml;

        if (data.getPersonalInfo() != null) {
            result = result.replace("{{name}}", escapeHtml(data.getPersonalInfo().getName()));
            result = result.replace("{{email}}", escapeHtml(data.getPersonalInfo().getEmail()));
            result = result.replace("{{phone}}", escapeHtml(data.getPersonalInfo().getPhone()));
            result = result.replace("{{location}}", escapeHtml(data.getPersonalInfo().getLocation()));
            result = result.replace("{{linkedin}}", escapeHtml(data.getPersonalInfo().getLinkedin()));
        }

        result = result.replace("{{summary}}", data.getSummary() != null ? escapeHtml(data.getSummary()) : "");

        if (data.getExperience() != null && !data.getExperience().isEmpty()) {
            StringBuilder experienceHtml = new StringBuilder();
            for (ResumeData.Experience exp : data.getExperience()) {
                experienceHtml.append("<div class=\"experience-item\">");
                experienceHtml.append("<div class=\"job-title\">").append(escapeHtml(exp.getTitle())).append("</div>");
                experienceHtml.append("<div class=\"company\">").append(escapeHtml(exp.getCompany())).append("</div>");
                if (exp.getResponsibilities() != null) {
                    experienceHtml.append("<ul>");
                    exp.getResponsibilities().forEach(resp ->
                            experienceHtml.append("<li>").append(escapeHtml(resp)).append("</li>"));
                    experienceHtml.append("</ul>");
                }
                experienceHtml.append("</div>");
            }
            result = result.replace("{{experience}}", experienceHtml.toString());
        }

        if (data.getSkills() != null && data.getSkills().getTechnical() != null) {
            StringBuilder skillsHtml = new StringBuilder();
            data.getSkills().getTechnical().forEach(skill ->
                    skillsHtml.append("<span class=\"skill-tag\">").append(escapeHtml(skill)).append("</span>"));
            result = result.replace("{{skills}}", skillsHtml.toString());
        }

        if (data.getEducation() != null && !data.getEducation().isEmpty()) {
            StringBuilder eduHtml = new StringBuilder();
            data.getEducation().forEach(edu -> {
                eduHtml.append("<div class=\"education-item\">");
                eduHtml.append("<strong>").append(escapeHtml(edu.getDegree())).append("</strong>");
                eduHtml.append("<div>").append(escapeHtml(edu.getInstitution())).append("</div>");
                eduHtml.append("</div>");
            });
            result = result.replace("{{education}}", eduHtml.toString());
        }

        return result;
    }

    // ==================== CORE PROCESSING METHODS ====================

    private String parsePdfToText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private ResumeSections parseResumeSection(String text) {
        ResumeSections sections = new ResumeSections();

        // Extract contact info from top of resume
        String[] lines = text.split("\n");
        StringBuilder contactInfo = new StringBuilder();
        for (int i = 0; i < Math.min(10, lines.length); i++) {
            contactInfo.append(lines[i]).append("\n");
        }
        sections.contactInfo = contactInfo.toString().trim();

        // Define section markers
        String[] sectionHeaders = {
                "EXPERIENCE", "WORK EXPERIENCE", "PROFESSIONAL EXPERIENCE",
                "EDUCATION", "ACADEMIC BACKGROUND",
                "SKILLS", "TECHNICAL SKILLS", "CORE COMPETENCIES",
                "PROJECTS", "KEY PROJECTS",
                "CERTIFICATIONS", "CERTIFICATES",
                "SUMMARY", "PROFESSIONAL SUMMARY", "OBJECTIVE"
        };

        // Extract sections
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String upperLine = line.trim().toUpperCase();

            // Check if line is a section header
            boolean isHeader = false;
            for (String header : sectionHeaders) {
                if (upperLine.startsWith(header) || upperLine.equals(header)) {
                    // Save previous section
                    if (currentSection != null && currentContent.length() > 0) {
                        sections.addSection(currentSection, currentContent.toString());
                    }
                    currentSection = header;
                    currentContent = new StringBuilder();
                    isHeader = true;
                    break;
                }
            }

            if (!isHeader && currentSection != null) {
                currentContent.append(line).append("\n");
            }
        }

        // Save last section
        if (currentSection != null && currentContent.length() > 0) {
            sections.addSection(currentSection, currentContent.toString());
        }

        log.info("📊 Parsed {} sections from resume", sections.getSectionCount());
        return sections;
    }

    private String extractContactInfo(String text) {
        StringBuilder contact = new StringBuilder();

        // Extract email
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher emailMatcher = emailPattern.matcher(text);
        if (emailMatcher.find()) {
            contact.append("Email: ").append(emailMatcher.group()).append("\n");
        }

        // Extract phone
        Pattern phonePattern = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
        Matcher phoneMatcher = phonePattern.matcher(text);
        if (phoneMatcher.find()) {
            contact.append("Phone: ").append(phoneMatcher.group()).append("\n");
        }

        // Extract LinkedIn
        Pattern linkedinPattern = Pattern.compile("linkedin\\.com/in/[\\w-]+", Pattern.CASE_INSENSITIVE);
        Matcher linkedinMatcher = linkedinPattern.matcher(text);
        if (linkedinMatcher.find()) {
            contact.append("LinkedIn: ").append(linkedinMatcher.group()).append("\n");
        }

        return contact.toString();
    }

    private String convertSectionsToJson(ResumeSections sections) throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Convert the following resume sections into structured JSON format.\n\n");
        prompt.append("CONTACT INFO:\n").append(sections.contactInfo).append("\n\n");

        for (Map.Entry<String, String> entry : sections.sections.entrySet()) {
            prompt.append(entry.getKey()).append(":\n");
            prompt.append(entry.getValue()).append("\n\n");
        }

        prompt.append("\nReturn ONLY valid JSON in this exact format (no markdown, no explanation):\n");
        prompt.append("{\n");
        prompt.append("  \"personalInfo\": {\"name\": \"\", \"email\": \"\", \"phone\": \"\", \"location\": \"\", \"linkedin\": \"\"},\n");
        prompt.append("  \"summary\": \"\",\n");
        prompt.append("  \"experience\": [{\"title\": \"\", \"company\": \"\", \"duration\": \"\", \"responsibilities\": []}],\n");
        prompt.append("  \"education\": [{\"degree\": \"\", \"institution\": \"\", \"year\": \"\"}],\n");
        prompt.append("  \"skills\": {\"technical\": [], \"soft\": []},\n");
        prompt.append("  \"projects\": [{\"name\": \"\", \"description\": \"\", \"technologies\": []}],\n");
        prompt.append("  \"certifications\": []\n");
        prompt.append("}\n");

        log.info("🤖 Calling Gemini to convert sections to JSON...");
        return callGemini(prompt.toString(), 4000);
    }

    private String transformResumeOptimized(String resumeJson, String jobDescription) throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert resume optimizer. Given the following resume in JSON format and a job description, ");
        prompt.append("optimize the resume to match the job requirements.\n\n");
        prompt.append("RULES:\n");
        prompt.append("1. Keep the same JSON structure\n");
        prompt.append("2. Enhance bullet points with relevant keywords from job description\n");
        prompt.append("3. Prioritize relevant skills and experience\n");
        prompt.append("4. Keep all information truthful - only rephrase, don't fabricate\n");
        prompt.append("5. Return ONLY valid JSON (no markdown, no explanation)\n\n");

        prompt.append("JOB DESCRIPTION:\n").append(jobDescription).append("\n\n");
        prompt.append("CURRENT RESUME JSON:\n").append(resumeJson).append("\n\n");
        prompt.append("Return the optimized resume in the SAME JSON format:\n");

        log.info("🎯 Calling Gemini to optimize resume for job description...");
        return callGemini(prompt.toString(), 4000);
    }

    private String callGemini(String prompt, int maxTokens) throws Exception {
        if (workingModel == null || workingApiBase == null) {
            throw new RuntimeException("No working Gemini model available");
        }

        log.info("📡 Calling Gemini API: {} with {} chars prompt", workingModel, prompt.length());

        // Build request - Java 17 compatible way
        Map<String, Object> requestMap = new HashMap<>();
        List<Map<String, Object>> contentsArray = new ArrayList<>();
        List<Map<String, String>> partsArray = new ArrayList<>();

        // Create parts map
        Map<String, String> partMap = new HashMap<>();
        partMap.put("text", prompt);
        partsArray.add(partMap);

        // Create contents map
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("parts", partsArray);
        contentsArray.add(contentMap);

        requestMap.put("contents", contentsArray);

        // Add generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens);
        generationConfig.put("temperature", 0.7);
        requestMap.put("generationConfig", generationConfig);

        // Make request
        String url = workingApiBase + workingModel + ":generateContent?key=" + geminiApiKey;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestMap)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("❌ Gemini API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini API error: " + response.statusCode());
        }

        // Parse response
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidates = root.get("candidates");

        if (candidates == null || candidates.size() == 0) {
            throw new RuntimeException("No candidates in Gemini response");
        }

        JsonNode content = candidates.get(0).get("content");
        JsonNode parts = content.get("parts");

        if (parts == null || parts.size() == 0) {
            throw new RuntimeException("No parts in Gemini response");
        }

        String text = parts.get(0).get("text").asText();
        log.info("✅ Gemini response received: {} chars", text.length());

        return cleanJsonResponse(text);
    }

    private String cleanJsonResponse(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // Remove markdown code blocks
        content = content.replaceAll("```json\\s*", "");
        content = content.replaceAll("```\\s*", "");

        // Remove any leading/trailing whitespace
        content = content.trim();

        // Find the first { and last }
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1);
        }

        log.info("🧹 Cleaned JSON response: {} chars", content.length());
        return content;
    }


    private String generateHtmlFromJson(ResumeData data) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; max-width: 800px; margin: 40px auto; padding: 20px; }");
        html.append("h1 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }");
        html.append("h2 { color: #34495e; margin-top: 30px; border-bottom: 2px solid #ecf0f1; padding-bottom: 5px; }");
        html.append(".contact-info { color: #7f8c8d; margin-bottom: 20px; }");
        html.append(".experience-item { margin: 15px 0; }");
        html.append(".job-title { font-weight: bold; color: #2980b9; }");
        html.append(".company { color: #7f8c8d; font-style: italic; }");
        html.append(".skill-tag { display: inline-block; background: #ecf0f1; padding: 5px 10px; margin: 5px; border-radius: 3px; }");
        html.append("ul { margin: 10px 0; } li { margin: 5px 0; }");
        html.append("</style></head><body>");

        // Personal Info
        if (data.getPersonalInfo() != null) {
            html.append("<h1>").append(escapeHtml(data.getPersonalInfo().getName())).append("</h1>");
            html.append("<div class='contact-info'>");
            if (data.getPersonalInfo().getEmail() != null) {
                html.append("📧 ").append(escapeHtml(data.getPersonalInfo().getEmail())).append(" | ");
            }
            if (data.getPersonalInfo().getPhone() != null) {
                html.append("📱 ").append(escapeHtml(data.getPersonalInfo().getPhone())).append(" | ");
            }
            if (data.getPersonalInfo().getLocation() != null) {
                html.append("📍 ").append(escapeHtml(data.getPersonalInfo().getLocation()));
            }
            html.append("</div>");
        }

        // Summary
        if (data.getSummary() != null && !data.getSummary().isEmpty()) {
            html.append("<h2>Professional Summary</h2>");
            html.append("<p>").append(escapeHtml(data.getSummary())).append("</p>");
        }

        // Experience
        if (data.getExperience() != null && !data.getExperience().isEmpty()) {
            html.append("<h2>Work Experience</h2>");
            for (ResumeData.Experience exp : data.getExperience()) {
                html.append("<div class='experience-item'>");
                html.append("<div class='job-title'>").append(escapeHtml(exp.getTitle())).append("</div>");
                html.append("<div class='company'>").append(escapeHtml(exp.getCompany()));
                if (exp.getDuration() != null) {
                    html.append(" | ").append(escapeHtml(exp.getDuration()));
                }
                html.append("</div>");
                if (exp.getResponsibilities() != null && !exp.getResponsibilities().isEmpty()) {
                    html.append("<ul>");
                    for (String resp : exp.getResponsibilities()) {
                        html.append("<li>").append(escapeHtml(resp)).append("</li>");
                    }
                    html.append("</ul>");
                }
                html.append("</div>");
            }
        }

        // Skills
        if (data.getSkills() != null && data.getSkills().getTechnical() != null) {
            html.append("<h2>Technical Skills</h2>");
            html.append("<div>");
            for (String skill : data.getSkills().getTechnical()) {
                html.append("<span class='skill-tag'>").append(escapeHtml(skill)).append("</span>");
            }
            html.append("</div>");
        }

        // Education
        if (data.getEducation() != null && !data.getEducation().isEmpty()) {
            html.append("<h2>Education</h2>");
            for (ResumeData.Education edu : data.getEducation()) {
                html.append("<div class='experience-item'>");
                html.append("<strong>").append(escapeHtml(edu.getDegree())).append("</strong><br>");
                html.append(escapeHtml(edu.getInstitution()));
                if (edu.getYear() != null) {
                    html.append(" | ").append(escapeHtml(edu.getYear()));
                }
                html.append("</div>");
            }
        }

        // Projects
        if (data.getProjects() != null && !data.getProjects().isEmpty()) {
            html.append("<h2>Projects</h2>");
            for (ResumeData.Project project : data.getProjects()) {
                html.append("<div class='experience-item'>");
                html.append("<strong>").append(escapeHtml(project.getName())).append("</strong><br>");
                html.append("<p>").append(escapeHtml(project.getDescription())).append("</p>");
                if (project.getTechnologies() != null) {
                    html.append("<div>");
                    for (String tech : project.getTechnologies()) {
                        html.append("<span class='skill-tag'>").append(escapeHtml(tech)).append("</span>");
                    }
                    html.append("</div>");
                }
                html.append("</div>");
            }
        }

        // Certifications
        if (data.getCertifications() != null && !data.getCertifications().isEmpty()) {
            html.append("<h2>Certifications</h2>");
            html.append("<ul>");
            for (String cert : data.getCertifications()) {
                html.append("<li>").append(escapeHtml(cert)).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("</body></html>");

        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void convertHtmlToPdf(String html, String outputPath) throws Exception {
        try (OutputStream os = new FileOutputStream(outputPath)) {
            org.xhtmlrenderer.pdf.ITextRenderer renderer =
                    new org.xhtmlrenderer.pdf.ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(os);
        }
    }

    // ==================== INNER CLASS ====================

    private static class ResumeSections {
        String contactInfo = "";
        Map<String, String> sections = new HashMap<>();

        void addSection(String name, String content) {
            if (content != null && !content.trim().isEmpty()) {
                sections.put(name, content.trim());
            }
        }

        int getSectionCount() {
            return sections.size();
        }
    }

}