package com.contractrisk.service;

import com.contractrisk.config.ContractConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private final ContractConfig contractConfig;
    private final OCRService ocrService;

    public String parseDocument(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String fileType = getFileExtension(originalFileName).toLowerCase();
        String storedFileName = UUID.randomUUID() + "_" + originalFileName;
        Path uploadPath = Path.of(contractConfig.getUpload().getPath());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(storedFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String text;
        switch (fileType) {
            case "pdf":
                text = parsePDF(filePath.toFile());
                break;
            case "doc":
                text = parseDoc(filePath.toFile());
                break;
            case "docx":
                text = parseDocx(filePath.toFile());
                break;
            default:
                throw new IllegalArgumentException("不支持的文件格式: " + fileType);
        }

        return text;
    }

    public String parseDocument(File file, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> parsePDF(file);
            case "doc" -> parseDoc(file);
            case "docx" -> parseDocx(file);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + fileType);
        };
    }

    private String parsePDF(File pdfFile) throws IOException {
        StringBuilder text = new StringBuilder();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String extractedText = stripper.getText(document);

            if (extractedText.trim().length() < 50 && contractConfig.getOcr().isEnabled()) {
                log.info("PDF文本内容过少，尝试OCR识别");
                String ocrText = ocrService.recognizeTextFromPDF(pdfFile);
                if (ocrText != null && ocrText.trim().length() > extractedText.trim().length()) {
                    text.append(ocrText);
                } else {
                    text.append(extractedText);
                }
            } else {
                text.append(extractedText);
            }
        }

        return cleanText(text.toString());
    }

    private String parseDoc(File docFile) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream is = new FileInputStream(docFile);
             HWPFDocument document = new HWPFDocument(is)) {
            Range range = document.getRange();
            text.append(range.text());
        }
        return cleanText(text.toString());
    }

    private String parseDocx(File docxFile) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream is = new FileInputStream(docxFile);
             XWPFDocument document = new XWPFDocument(is)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append("\n");
                } else {
                    text.append(paragraphText).append("\n");
                }
            }
        }
        return cleanText(text.toString());
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("\\r", "\n");
        text = text.replaceAll("[\\t\\f]", " ");
        text = text.replaceAll(" +", " ");
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    public String getFileType(String fileName) {
        return getFileExtension(fileName).toLowerCase();
    }

    public Path saveUploadedFile(MultipartFile file) throws IOException {
        String storedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = Path.of(contractConfig.getUpload().getPath());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(storedFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath;
    }
}
