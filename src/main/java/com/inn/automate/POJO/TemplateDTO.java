package com.inn.automate.POJO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for Template - Used in API responses to avoid sending htmlCode in list endpoints
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDTO {

    private Long templateId;
    private String name;
    private String description;
    private String previewImageUrl;
    private Boolean isActive;

    /**
     * Convert Entity to DTO
     */
    public static TemplateDTO fromEntity(Template template) {
        return new TemplateDTO(
                template.getTemplateId(),
                template.getName(),
                template.getDescription(),
                template.getPreviewImageUrl(),
                template.getIsActive()
        );
    }
}