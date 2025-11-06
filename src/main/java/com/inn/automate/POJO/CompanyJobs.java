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
public class CompanyJobs {
    private String companyName;
    private String industry;
    private String companySize;
    private String website;
    private List<JobListing> jobs;
}