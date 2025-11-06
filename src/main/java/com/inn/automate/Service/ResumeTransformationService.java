package com.inn.automate.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inn.automate.POJO.ResumeData;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ResumeTransformationService {

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

    /**
     * Auto-detect available Gemini model on startup
     */
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
            String apiBase = combo[0];
            String model = combo[1];

            if (testModel(apiBase, model)) {
                workingModel = model;
                workingApiBase = apiBase;
                log.info("✅ SUCCESS! Using model: {} with API: {}", model, apiBase);
                return;
            }
        }

        log.error("❌ No working Gemini model found!");
        listAvailableModels();
    }

    private boolean testModel(String apiBase, String model) {
        try {
            log.info("Testing: {} with {}", model, apiBase);

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

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                if (jsonResponse.has("candidates")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void listAvailableModels() {
        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models";
            String url = apiUrl + "?key=" + geminiApiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                if (jsonResponse.has("models")) {
                    JsonNode models = jsonResponse.get("models");
                    for (JsonNode modelNode : models) {
                        String modelName = modelNode.get("name").asText().replace("models/", "");
                        JsonNode methods = modelNode.get("supportedGenerationMethods");
                        if (methods != null) {
                            for (JsonNode method : methods) {
                                if (method.asText().equals("generateContent")) {
                                    if (testModel(apiUrl + "/", modelName)) {
                                        workingModel = modelName;
                                        workingApiBase = apiUrl + "/";
                                        log.info("✅ Auto-selected: {}", modelName);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error listing models: {}", e.getMessage());
        }
    }

    /**
     * OPTIMIZED: Main method to transform resume
     */
    public String transformResume(MultipartFile pdfFile, String jobDescription) throws Exception {
        if (workingModel == null || workingApiBase == null) {
            throw new RuntimeException("No working Gemini model available!");
        }

        log.info("🚀 Starting optimized resume transformation...");

        // Step 1: Extract text from PDF
        log.info("📄 Step 1: Extracting text from PDF...");
        String resumeText = parsePdfToText(pdfFile);
        log.info("✅ Extracted {} characters", resumeText.length());

        // Step 2: Parse text into structured sections (pre-processing)
        log.info("🔍 Step 2: Parsing resume sections...");
        ResumeSections sections = parseResumeSection(resumeText);
        log.info("✅ Parsed {} sections", sections.getSectionCount());

        // Step 3: Convert sections to JSON using smaller AI calls
        log.info("🤖 Step 3: Converting to JSON (optimized)...");
        String resumeJson = convertSectionsToJson(sections);
        log.info("✅ JSON created: {} characters", resumeJson.length());

        // Step 4: Transform with AI (optimized prompt)
        log.info("✨ Step 4: Transforming for job description...");
        String transformedJson = transformResumeOptimized(resumeJson, jobDescription);
        log.info("✅ Transformation complete");

        // Step 5: Generate PDF
        log.info("📝 Step 5: Generating PDF...");
        String outputPath = generatePdfFromJson(transformedJson);
        log.info("✅ PDF generated: {}", outputPath);

        return outputPath;
    }

    /**
     * Parse PDF to text
     */
    private String parsePdfToText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Pre-parse resume into sections using regex (no AI needed)
     */
    private ResumeSections parseResumeSection(String text) {
        ResumeSections sections = new ResumeSections();

        // Extract contact info
        sections.contactInfo = extractContactInfo(text);

        // Split into sections
        String[] lines = text.split("\\r?\\n");
        StringBuilder currentSection = new StringBuilder();
        String currentSectionName = "header";

        for (String line : lines) {
            String lowerLine = line.toLowerCase().trim();

            // Check for section headers
            if (lowerLine.matches("^(summary|profile|objective|about).*")) {
                sections.addSection(currentSectionName, currentSection.toString());
                currentSectionName = "summary";
                currentSection = new StringBuilder();
            } else if (lowerLine.matches("^(experience|work experience|employment|professional experience).*")) {
                sections.addSection(currentSectionName, currentSection.toString());
                currentSectionName = "experience";
                currentSection = new StringBuilder();
            } else if (lowerLine.matches("^(education|academic|qualifications).*")) {
                sections.addSection(currentSectionName, currentSection.toString());
                currentSectionName = "education";
                currentSection = new StringBuilder();
            } else if (lowerLine.matches("^(skills|technical skills|competencies).*")) {
                sections.addSection(currentSectionName, currentSection.toString());
                currentSectionName = "skills";
                currentSection = new StringBuilder();
            } else if (lowerLine.matches("^(projects|personal projects).*")) {
                sections.addSection(currentSectionName, currentSection.toString());
                currentSectionName = "projects";
                currentSection = new StringBuilder();
            } else if (lowerLine.matches("^(certifications|certificates|licenses).*")) {
                sections.addSection(currentSectionName, currentSection.toString());
                currentSectionName = "certifications";
                currentSection = new StringBuilder();
            } else {
                currentSection.append(line).append("\n");
            }
        }

        sections.addSection(currentSectionName, currentSection.toString());
        return sections;
    }

    /**
     * Extract contact info using regex
     */
    private String extractContactInfo(String text) {
        StringBuilder contact = new StringBuilder();

        // Email
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher emailMatcher = emailPattern.matcher(text);
        if (emailMatcher.find()) {
            contact.append("Email: ").append(emailMatcher.group()).append("\n");
        }

        // Phone
        Pattern phonePattern = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
        Matcher phoneMatcher = phonePattern.matcher(text);
        if (phoneMatcher.find()) {
            contact.append("Phone: ").append(phoneMatcher.group()).append("\n");
        }

        return contact.toString();
    }

    /**
     * Convert parsed sections to JSON using smaller, focused AI calls
     */
    private String convertSectionsToJson(ResumeSections sections) throws Exception {
        // Use a simpler, more direct prompt
        String prompt = String.format("""
            Convert this resume to JSON. Be concise and direct.
            
            Contact: %s
            
            Summary: %s
            
            Experience: %s
            
            Education: %s
            
            Skills: %s
            
            Return ONLY this JSON structure (no markdown):
            {
              "personalInfo": {"name": "", "email": "", "phone": "", "location": "", "linkedin": "", "portfolio": ""},
              "summary": "",
              "experience": [{"title": "", "company": "", "location": "", "startDate": "", "endDate": "", "responsibilities": []}],
              "education": [{"degree": "", "institution": "", "graduationDate": ""}],
              "skills": {"technical": [], "tools": []},
              "projects": [{"name": "", "description": "", "technologies": []}],
              "certifications": []
            }
            """,
                truncate(sections.contactInfo, 200),
                truncate(sections.sections.getOrDefault("summary", ""), 500),
                truncate(sections.sections.getOrDefault("experience", ""), 1500),
                truncate(sections.sections.getOrDefault("education", ""), 500),
                truncate(sections.sections.getOrDefault("skills", ""), 500)
        );

        return callGemini(prompt, 4000); // Reduced max tokens
    }

    /**
     * Optimized transformation with shorter prompt
     */
    private String transformResumeOptimized(String resumeJson, String jobDescription) throws Exception {
        // Truncate job description if too long
        String truncatedJob = truncate(jobDescription, 500);

        String prompt = String.format("""
            Optimize this resume for the job. Keep same JSON structure.
            
            Job: %s
            
            Resume JSON:
            %s
            
            Instructions:
            1. Rewrite summary to match job
            2. Emphasize relevant skills
            3. Keep all data, just reorder/rephrase
            4. Return ONLY valid JSON
            """,
                truncatedJob,
                resumeJson
        );

        return callGemini(prompt, 4000);
    }

    /**
     * Optimized Gemini API call with timeout handling
     */
    private String callGemini(String prompt, int maxTokens) throws Exception {
        log.info("🔄 Calling Gemini API (max tokens: {})...", maxTokens);

        var contentsArray = new ArrayList<java.util.Map<String, Object>>();
        var partsArray = new ArrayList<java.util.Map<String, String>>();
        partsArray.add(java.util.Map.of("text", prompt));
        contentsArray.add(java.util.Map.of("parts", partsArray));

        var requestMap = new java.util.HashMap<String, Object>();
        requestMap.put("contents", contentsArray);

        // Optimized generation config
        var generationConfig = new java.util.HashMap<String, Object>();
        generationConfig.put("temperature", 0.5); // Lower for more focused responses
        generationConfig.put("topK", 20); // Reduced for faster generation
        generationConfig.put("topP", 0.8);
        generationConfig.put("maxOutputTokens", maxTokens);
        requestMap.put("generationConfig", generationConfig);

        String requestBody = objectMapper.writeValueAsString(requestMap);
        String url = workingApiBase + workingModel + ":generateContent?key=" + geminiApiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60)) // 60 second timeout
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("API Error: {}", response.body());
            throw new RuntimeException("Gemini API failed: " + response.body());
        }

        JsonNode jsonResponse = objectMapper.readTree(response.body());

        if (jsonResponse.has("error")) {
            throw new RuntimeException("Gemini error: " + jsonResponse.get("error"));
        }

        JsonNode candidates = jsonResponse.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("No response from Gemini");
        }

        String content = candidates.get(0)
                .get("content")
                .get("parts")
                .get(0)
                .get("text")
                .asText();

        return cleanJsonResponse(content);
    }

    /**
     * Truncate text to max length
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * Clean JSON response
     */
    private String cleanJsonResponse(String content) {
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    /**
     * Generate PDF from JSON
     */
    private String generatePdfFromJson(String json) throws Exception {
        ResumeData resumeData = objectMapper.readValue(json, ResumeData.class);

        Path outputDir = Paths.get(outputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "resume_" + timestamp + ".pdf";
        Path outputPath = outputDir.resolve(filename);

        String html = generateHtmlFromJson(resumeData);
        convertHtmlToPdf(html, outputPath.toString());

        return outputPath.toString();
    }

    private String generateHtmlFromJson(ResumeData data) {
        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style type=\"text/css\">");
        html.append("""
            body { font-family: Arial, sans-serif; margin: 40px; color: #333; line-height: 1.6; }
            h1 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; margin-bottom: 10px; }
            h2 { color: #34495e; margin-top: 25px; border-bottom: 2px solid #bdc3c7; padding-bottom: 5px; }
            .contact { margin: 10px 0; color: #555; }
            .experience-item, .education-item, .project-item { margin: 15px 0; }
            .job-title { font-weight: bold; color: #2980b9; font-size: 1.1em; }
            .company { font-style: italic; color: #555; }
            .date { color: #777; font-size: 0.9em; }
            ul { margin: 8px 0; padding-left: 25px; }
            li { margin: 5px 0; }
            .skills { margin: 10px 0; }
            .skill-tag { background: #ecf0f1; padding: 6px 12px; margin: 5px; border-radius: 4px; display: inline-block; }
            .summary { background: #f8f9fa; padding: 15px; border-left: 4px solid #3498db; margin: 15px 0; }
            """);
        html.append("</style></head><body>");

        // Personal Info
        if (data.getPersonalInfo() != null) {
            html.append("<h1>").append(escapeHtml(data.getPersonalInfo().getName())).append("</h1>");
            html.append("<div class=\"contact\">");
            if (data.getPersonalInfo().getEmail() != null) {
                html.append(escapeHtml(data.getPersonalInfo().getEmail()));
            }
            if (data.getPersonalInfo().getPhone() != null) {
                html.append(" | ").append(escapeHtml(data.getPersonalInfo().getPhone()));
            }
            if (data.getPersonalInfo().getLocation() != null) {
                html.append(" | ").append(escapeHtml(data.getPersonalInfo().getLocation()));
            }
            if (data.getPersonalInfo().getLinkedin() != null && !data.getPersonalInfo().getLinkedin().isEmpty()) {
                html.append(" | ").append(escapeHtml(data.getPersonalInfo().getLinkedin()));
            }
            html.append("</div>");
        }

        // Summary
        if (data.getSummary() != null && !data.getSummary().isEmpty()) {
            html.append("<div class=\"summary\">").append(escapeHtml(data.getSummary())).append("</div>");
        }

        // Experience
        if (data.getExperience() != null && !data.getExperience().isEmpty()) {
            html.append("<h2>Professional Experience</h2>");
            data.getExperience().forEach(exp -> {
                html.append("<div class=\"experience-item\">");
                html.append("<div class=\"job-title\">").append(escapeHtml(exp.getTitle())).append("</div>");
                html.append("<div class=\"company\">").append(escapeHtml(exp.getCompany()));
                if (exp.getLocation() != null && !exp.getLocation().isEmpty()) {
                    html.append(", ").append(escapeHtml(exp.getLocation()));
                }
                html.append("</div>");
                if (exp.getStartDate() != null || exp.getEndDate() != null) {
                    html.append("<div class=\"date\">");
                    if (exp.getStartDate() != null) html.append(escapeHtml(exp.getStartDate()));
                    html.append(" - ");
                    if (exp.getEndDate() != null) html.append(escapeHtml(exp.getEndDate()));
                    html.append("</div>");
                }
                if (exp.getResponsibilities() != null && !exp.getResponsibilities().isEmpty()) {
                    html.append("<ul>");
                    exp.getResponsibilities().forEach(resp ->
                            html.append("<li>").append(escapeHtml(resp)).append("</li>"));
                    html.append("</ul>");
                }
                html.append("</div>");
            });
        }

        // Education
        if (data.getEducation() != null && !data.getEducation().isEmpty()) {
            html.append("<h2>Education</h2>");
            data.getEducation().forEach(edu -> {
                html.append("<div class=\"education-item\">");
                if (edu.getDegree() != null) {
                    html.append("<strong>").append(escapeHtml(edu.getDegree())).append("</strong>");
                }
                if (edu.getInstitution() != null) {
                    html.append("<div>").append(escapeHtml(edu.getInstitution())).append("</div>");
                }
                if (edu.getGraduationDate() != null) {
                    html.append("<div class=\"date\">").append(escapeHtml(edu.getGraduationDate())).append("</div>");
                }
                html.append("</div>");
            });
        }

        // Skills
        if (data.getSkills() != null) {
            boolean hasSkills = false;
            StringBuilder skillsHtml = new StringBuilder();

            if (data.getSkills().getTechnical() != null && !data.getSkills().getTechnical().isEmpty()) {
                hasSkills = true;
                data.getSkills().getTechnical().forEach(skill ->
                        skillsHtml.append("<span class=\"skill-tag\">").append(escapeHtml(skill)).append("</span>"));
            }
            if (data.getSkills().getTools() != null && !data.getSkills().getTools().isEmpty()) {
                hasSkills = true;
                data.getSkills().getTools().forEach(tool ->
                        skillsHtml.append("<span class=\"skill-tag\">").append(escapeHtml(tool)).append("</span>"));
            }

            if (hasSkills) {
                html.append("<h2>Skills</h2><div class=\"skills\">");
                html.append(skillsHtml);
                html.append("</div>");
            }
        }

        // Projects
        if (data.getProjects() != null && !data.getProjects().isEmpty()) {
            html.append("<h2>Projects</h2>");
            data.getProjects().forEach(proj -> {
                html.append("<div class=\"project-item\">");
                if (proj.getName() != null) {
                    html.append("<strong>").append(escapeHtml(proj.getName())).append("</strong>");
                }
                if (proj.getDescription() != null) {
                    html.append("<div>").append(escapeHtml(proj.getDescription())).append("</div>");
                }
                if (proj.getTechnologies() != null && !proj.getTechnologies().isEmpty()) {
                    html.append("<div><em>Technologies: ")
                            .append(escapeHtml(String.join(", ", proj.getTechnologies()))).append("</em></div>");
                }
                html.append("</div>");
            });
        }

        // Certifications
        if (data.getCertifications() != null && !data.getCertifications().isEmpty()) {
            html.append("<h2>Certifications</h2><ul>");
            data.getCertifications().forEach(cert ->
                    html.append("<li>").append(escapeHtml(cert)).append("</li>"));
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

    /**
     * Helper class to store parsed resume sections
     */
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