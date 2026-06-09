package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionUploadRequest {

    private String versionNote;
    private String uploadedBy;
}
