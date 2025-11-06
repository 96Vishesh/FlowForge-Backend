package com.inn.automate.POJO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeData {
    private PersonalInfo personalInfo;
    private String summary;
    private List<Experience> experience;
    private List<Education> education;
    private Skills skills;
    private List<Project> projects;
    private List<String> certifications;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersonalInfo {
        private String name;
        private String email;
        private String phone;
        private String location;
        private String linkedin;
        private String portfolio;
        private String github;
        private String website;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        private String title;
        private String company;
        private String location;
        private String startDate;
        private String endDate;
        private List<String> responsibilities;
        private List<String> achievements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Education {
        private String degree;
        private String institution;
        private String location;
        private String startDate;
        private String endDate;
        private String graduationDate;
        private String gpa;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Skills {
        private List<String> technical;
        private List<String> soft;
        private List<String> tools;
        private List<String> languages;
        private List<String> frameworks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        private String name;
        private String description;
        private List<String> technologies;
        private String link;
        private String github;
        private String startDate;
        private String endDate;
    }
}