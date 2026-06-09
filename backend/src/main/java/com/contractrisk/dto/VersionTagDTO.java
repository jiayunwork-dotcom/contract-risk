package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionTagDTO {

    private Long id;
    private String name;
    private String color;
    private boolean predefined;
}
