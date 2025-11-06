package com.inn.automate.POJO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResponse {
    private boolean success;
    private String message;
    private List<JobMatch> matches;
    private int totalMatches;
    private ResumeProfile resumeProfile;
    private String error;

    public static JobMatchResponse success(List<JobMatch> matches, ResumeProfile profile) {
        JobMatchResponse response = new JobMatchResponse();
        response.setSuccess(true);
        response.setMessage("Job matching completed successfully");
        response.setMatches(matches);
        response.setTotalMatches(matches.size());
        response.setResumeProfile(profile);
        return response;
    }

    public static JobMatchResponse error(String errorMessage) {
        JobMatchResponse response = new JobMatchResponse();
        response.setSuccess(false);
        response.setError(errorMessage);
        response.setMessage("Job matching failed");
        return response;
    }
}