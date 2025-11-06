package com.inn.automate.POJO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatch {
    private String companyName;
    private String jobTitle;
    private String jobProfile;
    private String jobDescription;
    private List<String> requiredSkills;
    private String experienceRequired;
    private String location;
    private double compatibilityScore;
    private List<String> matchedSkills;
    private String matchReason;
}