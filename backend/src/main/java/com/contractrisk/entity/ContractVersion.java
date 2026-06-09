package com.contractrisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contract_versions")
public class ContractVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "version_label", length = 20, nullable = false)
    private String versionLabel;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "version_note", columnDefinition = "TEXT")
    private String versionNote;

    @Column(name = "full_text", columnDefinition = "TEXT")
    private String fullText;

    @Column(name = "original_file_name", length = 200)
    private String originalFileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "is_current", nullable = false)
    private boolean current = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
