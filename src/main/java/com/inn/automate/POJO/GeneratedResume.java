package com.inn.automate.POJO;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generated_id")
    private Long generatedId;

    @Column(name = "user_id", nullable = false)
    private String userId;  // Links to User.id

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;  // Links to TransformedResume.resumeId

    @Column(name = "template_id", nullable = false)
    private Long templateId;  // Links to Template.templateId

    @Column(name = "pdf_file_path", nullable = false)
    private String pdfFilePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}