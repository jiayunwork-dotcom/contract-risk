package com.contractrisk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "contract")
public class ContractConfig {

    private Upload upload = new Upload();
    private Ocr ocr = new Ocr();
    private Risk risk = new Risk();
    private Approval approval = new Approval();

    @Data
    public static class Upload {
        private String path = "/tmp/contracts";
    }

    @Data
    public static class Ocr {
        private boolean enabled = true;
        private String dataPath = "/usr/share/tesseract-ocr/4.00/tessdata";
        private String language = "chi_sim+eng";
    }

    @Data
    public static class Risk {
        private int highPenalty = 20;
        private int mediumPenalty = 10;
        private int lowPenalty = 5;
        private int missingRequiredPenalty = 30;
        private int forbiddenClausePenalty = 50;
        private int rejectThreshold = 60;
    }

    @Data
    public static class Approval {
        private int autoEscalateHours = 48;
    }
}
