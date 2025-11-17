
package com.inn.automate.DAO;

import com.inn.automate.POJO.TransformedResume;
import com.inn.automate.POJO.GeneratedResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface GeneratedResumeRepository extends JpaRepository<GeneratedResume, Long> {

    /**
     * Find all generated resumes for a user
     */
    List<GeneratedResume> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find generated resumes by resume ID
     */
    List<GeneratedResume> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * Find specific generated resume
     */
    Optional<GeneratedResume> findByGeneratedIdAndUserId(Long generatedId, String userId);

    /**
     * Count generated resumes for a user
     */
    Long countByUserId(String userId);
}