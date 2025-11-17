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
public interface TransformedResumeRepository extends JpaRepository<TransformedResume, Long> {

    /**
     * Find all active resumes for a specific user
     */
    List<TransformedResume> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(String userId);

    /**
     * Find specific resume by ID and user ID (for security)
     */
    Optional<TransformedResume> findByResumeIdAndUserId(Long resumeId, String userId);

    /**
     * Get latest resume version for a user
     */
    @Query("SELECT MAX(t.version) FROM TransformedResume t WHERE t.userId = :userId")
    Integer getLatestVersionForUser(@Param("userId") String userId);

    /**
     * Count user's resumes
     */
    Long countByUserId(String userId);

    /**
     * Find the most recent resume for a user
     */
    Optional<TransformedResume> findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(String userId);
}
