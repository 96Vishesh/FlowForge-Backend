package com.inn.automate.POJO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobMatchRequest {
    private List<CompanyJobs> companies;

    // Support direct jobs array (flatten into single company)
    @JsonProperty("jobs")
    public void setJobs(List<JobListing> jobs) {
        if (this.companies == null) {
            this.companies = new ArrayList<>();
        }
        // Create a default company for direct job listings
        CompanyJobs defaultCompany = new CompanyJobs();
        defaultCompany.setCompanyName("Various Companies");
        defaultCompany.setJobs(jobs);
        this.companies.add(defaultCompany);
    }
}