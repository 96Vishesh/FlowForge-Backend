package com.inn.automate.POJO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeProfile {
    private Set<String> skills = new HashSet<>();
    private int experienceYears = 0;
    private List<String> education = new ArrayList<>();
    private List<String> certifications = new ArrayList<>();
    private String fullText;
}