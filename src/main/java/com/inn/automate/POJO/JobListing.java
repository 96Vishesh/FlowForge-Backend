package com.inn.automate.POJO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobListing {
    private String jobTitle;
    private String profile;
    private String description;
    private List<String> requiredSkills;
    private String experienceRequired;
    private String location;
    private String employmentType; // Full-time, Part-time, Contract
    private String salaryRange;

    // Support alternative field names
    private String company; // If job has company field directly
}