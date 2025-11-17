//package com.inn.automate.Service;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.inn.automate.DAO.TemplateRepository;
//import com.inn.automate.POJO.ResumeData;
//import com.inn.automate.POJO.Template;
//import com.inn.automate.POJO.TemplateDTO;
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
//
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
//
//        log.error("❌ No working Gemini model found!");
//        listAvailableModels();
//    }
//
//    private boolean testModel(String apiBase, String model) {
//        try {
//            log.info("Testing: {} with {}", model, apiBase);
//
//            var requestMap = new java.util.HashMap<String, Object>();
//            var contentsArray = new ArrayList<java.util.Map<String, Object>>();
//            var partsArray = new ArrayList<java.util.Map<String, String>>();
//            partsArray.add(java.util.Map.of("text", "test"));
//            contentsArray.add(java.util.Map.of("parts", partsArray));
//            requestMap.put("contents", contentsArray);
//
//            String url = apiBase + model + ":generateContent?key=" + geminiApiKey;
//
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(url))
//                    .header("Content-Type", "application/json")
//                    .timeout(Duration.ofSeconds(10))
//                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestMap)))
//                    .build();
//
//            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//            return response.statusCode() == 200 && objectMapper.readTree(response.body()).has("candidates");
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    private void listAvailableModels() {
//        try {
//            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models";
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(apiUrl + "?key=" + geminiApiKey))
//                    .GET()
//                    .build();
//
//            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//            if (response.statusCode() == 200) {
//                JsonNode jsonResponse = objectMapper.readTree(response.body());
//                if (jsonResponse.has("models")) {
//                    for (JsonNode modelNode : jsonResponse.get("models")) {
//                        String modelName = modelNode.get("name").asText().replace("models/", "");
//                        JsonNode methods = modelNode.get("supportedGenerationMethods");
//                        if (methods != null) {
//                            for (JsonNode method : methods) {
//                                if (method.asText().equals("generateContent")) {
//                                    if (testModel(apiUrl + "/", modelName)) {
//                                        workingModel = modelName;
//                                        workingApiBase = apiUrl + "/";
//                                        log.info("✅ Auto-selected: {}", modelName);
//                                        return;
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            log.error("Error listing models: {}", e.getMessage());
//        }
//    }
//
//    /**
//     * Main method to extract and transform resume into JSON
//     */
//    public String transformResume(MultipartFile pdfFile, String jobDescription) throws Exception {
//        if (workingModel == null || workingApiBase == null) {
//            throw new RuntimeException("No working Gemini model available!");
//        }
//
//        log.info("🚀 Starting optimized resume transformation (JSON output)...");
//
//        String resumeText = parsePdfToText(pdfFile);
//        log.info("✅ Extracted {} characters", resumeText.length());
//
//        ResumeSections sections = parseResumeSection(resumeText);
//        log.info("✅ Parsed {} sections", sections.getSectionCount());
//
//        String resumeJson = convertSectionsToJson(sections);
//        log.info("✅ JSON created: {} characters", resumeJson.length());
//
//        String transformedJson = transformResumeOptimized(resumeJson, jobDescription);
//        log.info("✅ Transformation complete");
//
//        return transformedJson;
//    }
//
//    /**
//     * Get available templates from database
//     */
//    public List<TemplateDTO> getAvailableTemplates() {
//        log.info("📋 Fetching available resume templates from database...");
//
//        List<Template> templates = templateRepository.findByIsActiveTrue();
//
//        if (templates.isEmpty()) {
//            log.warn("⚠️ No templates found in database, returning default templates");
//            return getDefaultTemplates();
//        }
//
//        log.info("✅ Successfully retrieved {} templates from database", templates.size());
//
//        return templates.stream()
//                .map(TemplateDTO::fromEntity)
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * Fallback: Default templates if database is empty
//     */
//    private List<TemplateDTO> getDefaultTemplates() {
//        List<TemplateDTO> templates = new ArrayList<>();
//        templates.add(new TemplateDTO(1L, "Modern Professional", "Clean, modern single-column design", null, true));
//        templates.add(new TemplateDTO(2L, "Classic Two-Column", "Traditional two-column layout", null, true));
//        templates.add(new TemplateDTO(3L, "Minimalist", "Minimal design highlighting skills", null, true));
//        return templates;
//    }
//
//    /**
//     * Generate PDF from JSON using selected template
//     */
//    public String generatePdfFromTemplate(String transformedJson, String templateId) throws Exception {
//        log.info("📝 Starting PDF generation using template ID: {}", templateId);
//
//        // Parse templateId - can be numeric ID or name
//        Template template = fetchTemplate(templateId);
//
//        if (template == null) {
//            log.warn("⚠️ Template not found: {}, using default template", templateId);
//            template = getDefaultTemplate();
//        }
//
//        ResumeData resumeData = objectMapper.readValue(transformedJson, ResumeData.class);
//
//        Path outputDir = Paths.get(outputDirectory);
//        if (!Files.exists(outputDir)) {
//            Files.createDirectories(outputDir);
//        }
//
//        String timestamp = LocalDateTime.now()
//                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String filename = "resume_" + templateId + "_" + timestamp + ".pdf";
//        Path outputPath = outputDir.resolve(filename);
//
//        // Use template HTML if available, otherwise use default
//        String finalHtml = template.getHtmlCode() != null ?
//                populateTemplate(template.getHtmlCode(), resumeData) :
//                generateHtmlFromJson(resumeData);
//
//        convertHtmlToPdf(finalHtml, outputPath.toString());
//
//        log.info("✅ PDF generated: {}", outputPath);
//        return outputPath.toString();
//    }
//
//    /**
//     * Fetch template by ID or name
//     */
//    private Template fetchTemplate(String templateId) {
//        try {
//            // Try parsing as Long (template ID)
//            Long id = Long.parseLong(templateId);
//            return templateRepository.findByTemplateIdAndIsActiveTrue(id).orElse(null);
//        } catch (NumberFormatException e) {
//            // If not a number, try as template name
//            return templateRepository.findByName(templateId).orElse(null);
//        }
//    }
//
//    /**
//     * Get default template when none is found
//     */
//    private Template getDefaultTemplate() {
//        Template defaultTemplate = new Template();
//        defaultTemplate.setTemplateId(0L);
//        defaultTemplate.setName("Default");
//        defaultTemplate.setDescription("Default built-in template");
//        defaultTemplate.setHtmlCode(null); // Will trigger default HTML generation
//        return defaultTemplate;
//    }
//
//    /**
//     * Populate template HTML with resume data using placeholders
//     */
//    private String populateTemplate(String templateHtml, ResumeData data) {
//        log.info("🔄 Populating template with resume data...");
//
//        String result = templateHtml;
//
//        // Personal Info placeholders
//        if (data.getPersonalInfo() != null) {
//            result = result.replace("{{name}}", escapeHtml(data.getPersonalInfo().getName()));
//            result = result.replace("{{email}}", escapeHtml(data.getPersonalInfo().getEmail()));
//            result = result.replace("{{phone}}", escapeHtml(data.getPersonalInfo().getPhone()));
//            result = result.replace("{{location}}", escapeHtml(data.getPersonalInfo().getLocation()));
//            result = result.replace("{{linkedin}}", escapeHtml(data.getPersonalInfo().getLinkedin()));
//            result = result.replace("{{portfolio}}", escapeHtml(data.getPersonalInfo().getPortfolio()));
//        }
//
//        // Summary
//        result = result.replace("{{summary}}", data.getSummary() != null ? escapeHtml(data.getSummary()) : "");
//
//        // Experience section
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
//        } else {
//            result = result.replace("{{experience}}", "");
//        }
//
//        // Skills
//        if (data.getSkills() != null && data.getSkills().getTechnical() != null) {
//            StringBuilder skillsHtml = new StringBuilder();
//            data.getSkills().getTechnical().forEach(skill ->
//                    skillsHtml.append("<span class=\"skill-tag\">").append(escapeHtml(skill)).append("</span>"));
//            result = result.replace("{{skills}}", skillsHtml.toString());
//        } else {
//            result = result.replace("{{skills}}", "");
//        }
//
//        // Education
//        if (data.getEducation() != null && !data.getEducation().isEmpty()) {
//            StringBuilder eduHtml = new StringBuilder();
//            data.getEducation().forEach(edu -> {
//                eduHtml.append("<div class=\"education-item\">");
//                eduHtml.append("<strong>").append(escapeHtml(edu.getDegree())).append("</strong>");
//                eduHtml.append("<div>").append(escapeHtml(edu.getInstitution())).append("</div>");
//                eduHtml.append("</div>");
//            });
//            result = result.replace("{{education}}", eduHtml.toString());
//        } else {
//            result = result.replace("{{education}}", "");
//        }
//
//        // Projects
//        if (data.getProjects() != null && !data.getProjects().isEmpty()) {
//            StringBuilder projHtml = new StringBuilder();
//            data.getProjects().forEach(proj -> {
//                projHtml.append("<div class=\"project-item\">");
//                if (proj.getName() != null) projHtml.append("<strong>").append(escapeHtml(proj.getName())).append("</strong>");
//                if (proj.getDescription() != null) projHtml.append("<div>").append(escapeHtml(proj.getDescription())).append("</div>");
//                projHtml.append("</div>");
//            });
//            result = result.replace("{{projects}}", projHtml.toString());
//        } else {
//            result = result.replace("{{projects}}", "");
//        }
//
//        log.info("✅ Template populated successfully");
//        return result;
//    }
//
//    // --- Core Processing Methods (Unchanged) ---
//
//    private String parsePdfToText(MultipartFile file) throws IOException {
//        try (InputStream inputStream = file.getInputStream();
//             PDDocument document = PDDocument.load(inputStream)) {
//            PDFTextStripper stripper = new PDFTextStripper();
//            return stripper.getText(document);
//        }
//    }
//
//    private ResumeSections parseResumeSection(String text) {
//        ResumeSections sections = new ResumeSections();
//        sections.contactInfo = extractContactInfo(text);
//
//        String[] lines = text.split("\\r?\\n");
//        StringBuilder currentSection = new StringBuilder();
//        String currentSectionName = "header";
//
//        for (String line : lines) {
//            String lowerLine = line.toLowerCase().trim();
//
//            if (lowerLine.matches("^(summary|profile|objective|about).*")) {
//                sections.addSection(currentSectionName, currentSection.toString());
//                currentSectionName = "summary";
//                currentSection = new StringBuilder();
//            } else if (lowerLine.matches("^(experience|work experience|employment|professional experience).*")) {
//                sections.addSection(currentSectionName, currentSection.toString());
//                currentSectionName = "experience";
//                currentSection = new StringBuilder();
//            } else if (lowerLine.matches("^(education|academic|qualifications).*")) {
//                sections.addSection(currentSectionName, currentSection.toString());
//                currentSectionName = "education";
//                currentSection = new StringBuilder();
//            } else if (lowerLine.matches("^(skills|technical skills|competencies).*")) {
//                sections.addSection(currentSectionName, currentSection.toString());
//                currentSectionName = "skills";
//                currentSection = new StringBuilder();
//            } else if (lowerLine.matches("^(projects|personal projects).*")) {
//                sections.addSection(currentSectionName, currentSection.toString());
//                currentSectionName = "projects";
//                currentSection = new StringBuilder();
//            } else if (lowerLine.matches("^(certifications|certificates|licenses).*")) {
//                sections.addSection(currentSectionName, currentSection.toString());
//                currentSectionName = "certifications";
//                currentSection = new StringBuilder();
//            } else {
//                currentSection.append(line).append("\n");
//            }
//        }
//
//        sections.addSection(currentSectionName, currentSection.toString());
//        return sections;
//    }
//
//    private String extractContactInfo(String text) {
//        StringBuilder contact = new StringBuilder();
//
//        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
//        Matcher emailMatcher = emailPattern.matcher(text);
//        if (emailMatcher.find()) {
//            contact.append("Email: ").append(emailMatcher.group()).append("\n");
//        }
//
//        Pattern phonePattern = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
//        Matcher phoneMatcher = phonePattern.matcher(text);
//        if (phoneMatcher.find()) {
//            contact.append("Phone: ").append(phoneMatcher.group()).append("\n");
//        }
//
//        return contact.toString();
//    }
//
//    private String convertSectionsToJson(ResumeSections sections) throws Exception {
//        String prompt = String.format("""
//            Convert this resume to JSON. Be concise and direct.
//
//            Contact: %s
//            Summary: %s
//            Experience: %s
//            Education: %s
//            Skills: %s
//
//            Return ONLY this JSON structure (no markdown):
//            {
//              "personalInfo": {"name": "", "email": "", "phone": "", "location": "", "linkedin": "", "portfolio": ""},
//              "summary": "",
//              "experience": [{"title": "", "company": "", "location": "", "startDate": "", "endDate": "", "responsibilities": []}],
//              "education": [{"degree": "", "institution": "", "graduationDate": ""}],
//              "skills": {"technical": [], "tools": []},
//              "projects": [{"name": "", "description": "", "technologies": []}],
//              "certifications": []
//            }
//            """,
//                truncate(sections.contactInfo, 200),
//                truncate(sections.sections.getOrDefault("summary", ""), 500),
//                truncate(sections.sections.getOrDefault("experience", ""), 1500),
//                truncate(sections.sections.getOrDefault("education", ""), 500),
//                truncate(sections.sections.getOrDefault("skills", ""), 500)
//        );
//
//        return callGemini(prompt, 4000);
//    }
//
//    private String transformResumeOptimized(String resumeJson, String jobDescription) throws Exception {
//        String prompt = String.format("""
//            Optimize this resume for the job. Keep same JSON structure.
//
//            Job: %s
//
//            Resume JSON: %s
//
//            Instructions:
//            1. Rewrite summary to match job
//            2. Emphasize relevant skills
//            3. Keep all data, just reorder/rephrase
//            4. Return ONLY valid JSON
//            """,
//                truncate(jobDescription, 500),
//                resumeJson
//        );
//
//        return callGemini(prompt, 4000);
//    }
//
//    private String callGemini(String prompt, int maxTokens) throws Exception {
//        log.info("🔄 Calling Gemini API (max tokens: {})...", maxTokens);
//
//        var contentsArray = new ArrayList<java.util.Map<String, Object>>();
//        var partsArray = new ArrayList<java.util.Map<String, String>>();
//        partsArray.add(java.util.Map.of("text", prompt));
//        contentsArray.add(java.util.Map.of("parts", partsArray));
//
//        var requestMap = new java.util.HashMap<String, Object>();
//        requestMap.put("contents", contentsArray);
//
//        var generationConfig = new java.util.HashMap<String, Object>();
//        generationConfig.put("temperature", 0.5);
//        generationConfig.put("topK", 20);
//        generationConfig.put("topP", 0.8);
//        generationConfig.put("maxOutputTokens", maxTokens);
//        requestMap.put("generationConfig", generationConfig);
//
//        String requestBody = objectMapper.writeValueAsString(requestMap);
//        String url = workingApiBase + workingModel + ":generateContent?key=" + geminiApiKey;
//
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(url))
//                .header("Content-Type", "application/json")
//                .timeout(Duration.ofSeconds(60))
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
//                .build();
//
//        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//        if (response.statusCode() != 200) {
//            log.error("API Error: {}", response.body());
//            throw new RuntimeException("Gemini API failed: " + response.body());
//        }
//
//        JsonNode jsonResponse = objectMapper.readTree(response.body());
//
//        if (jsonResponse.has("error")) {
//            throw new RuntimeException("Gemini error: " + jsonResponse.get("error"));
//        }
//
//        JsonNode candidates = jsonResponse.get("candidates");
//        if (candidates == null || candidates.isEmpty()) {
//            throw new RuntimeException("No response from Gemini");
//        }
//
//        String content = candidates.get(0).get("content").get("parts").get(0).get("text").asText();
//        return cleanJsonResponse(content);
//    }
//
//    private String truncate(String text, int maxLength) {
//        if (text == null) return "";
//        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
//    }
//
//    private String cleanJsonResponse(String content) {
//        content = content.trim();
//        if (content.startsWith("```json")) content = content.substring(7);
//        else if (content.startsWith("```")) content = content.substring(3);
//        if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
//        return content.trim();
//    }
//
//    private String generateHtmlFromJson(ResumeData data) {
//        // ... (keep your existing default HTML generation code)
//        // This is used as fallback when no template is found
//        StringBuilder html = new StringBuilder();
//        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
//        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
//        // ... rest of your default template code ...
//        return html.toString();
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
//
//        void addSection(String name, String content) {
//            if (content != null && !content.trim().isEmpty()) {
//                sections.put(name, content.trim());
//            }
//        }
//
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
import java.util.List;
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
            var requestMap = new java.util.HashMap<String, Object>();
            var contentsArray = new ArrayList<java.util.Map<String, Object>>();
            var partsArray = new ArrayList<java.util.Map<String, String>>();
            partsArray.add(java.util.Map.of("text", "test"));
            contentsArray.add(java.util.Map.of("parts", partsArray));
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

    // Core processing methods remain the same...
    private String parsePdfToText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private ResumeSections parseResumeSection(String text) {
        // ... (keep existing implementation)
        ResumeSections sections = new ResumeSections();
        sections.contactInfo = extractContactInfo(text);
        // ... rest of the parsing logic
        return sections;
    }

    private String extractContactInfo(String text) {
        // ... (keep existing implementation)
        return "";
    }

    private String convertSectionsToJson(ResumeSections sections) throws Exception {
        // ... (keep existing implementation)
        return callGemini("prompt", 4000);
    }

    private String transformResumeOptimized(String resumeJson, String jobDescription) throws Exception {
        // ... (keep existing implementation)
        return callGemini("prompt", 4000);
    }

    private String callGemini(String prompt, int maxTokens) throws Exception {
        // ... (keep existing implementation)
        return "";
    }

    private String cleanJsonResponse(String content) {
        // ... (keep existing implementation)
        return content.trim();
    }

    private String generateHtmlFromJson(ResumeData data) {
        // ... (keep existing default HTML generation)
        return "";
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

    private static class ResumeSections {
        String contactInfo = "";
        java.util.Map<String, String> sections = new java.util.HashMap<>();
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