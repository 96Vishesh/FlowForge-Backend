package com.inn.automate.DAO;

import com.inn.automate.POJO.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    /**
     * Find all active templates
     */
    List<Template> findByIsActiveTrue();

    /**
     * Find template by name
     */
    Optional<Template> findByName(String name);

    /**
     * Find template by ID only if active
     */
    Optional<Template> findByTemplateIdAndIsActiveTrue(Long templateId);

    /**
     * Custom query to get templates with minimal data (for listing)
     */
    @Query("SELECT new com.inn.automate.POJO.Template(t.templateId, t.name, t.description, null, t.previewImageUrl, t.isActive, null, null) " +
            "FROM Template t WHERE t.isActive = true")
    List<Template> findAllActiveTemplatesMinimal();
}