package com.contractrisk.service;

import com.contractrisk.config.ContractConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OCRService {

    private final ContractConfig contractConfig;

    private ITesseract tesseract;

    private ITesseract getTesseract() {
        if (tesseract == null) {
            tesseract = new Tesseract();
            try {
                tesseract.setDatapath(contractConfig.getOcr().getDataPath());
                tesseract.setLanguage(contractConfig.getOcr().getLanguage());
            } catch (Exception e) {
                log.warn("初始化Tesseract失败: {}", e.getMessage());
            }
        }
        return tesseract;
    }

    public String recognizeTextFromPDF(File pdfFile) {
        if (!contractConfig.getOcr().isEnabled()) {
            log.warn("OCR功能未启用");
            return "";
        }

        StringBuilder result = new StringBuilder();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int page = 0; page < pageCount; page++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                String pageText = recognizeText(bim);
                if (pageText != null && !pageText.isEmpty()) {
                    result.append(pageText).append("\n\n");
                }
            }
        } catch (IOException e) {
            log.error("解析PDF文件失败: {}", e.getMessage());
            return "";
        }

        return result.toString().trim();
    }

    public String recognizeText(BufferedImage image) {
        if (!contractConfig.getOcr().isEnabled()) {
            return "";
        }

        try {
            return getTesseract().doOCR(image);
        } catch (TesseractException e) {
            log.error("OCR识别失败: {}", e.getMessage());
            return "";
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("OCR库不可用: {}", e.getMessage());
            return "";
        }
    }

    public boolean isOcrAvailable() {
        if (!contractConfig.getOcr().isEnabled()) {
            return false;
        }
        try {
            getTesseract();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
