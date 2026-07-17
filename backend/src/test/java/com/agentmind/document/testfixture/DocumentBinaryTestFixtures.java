package com.agentmind.document.testfixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/** 在内存中生成可重复的 PDF、DOCX 和 Word 兼容 DOC 测试文档。 */
public final class DocumentBinaryTestFixtures {

    private DocumentBinaryTestFixtures() {
    }

    public static byte[] pdf(String title, List<String> pageTexts) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle(title);
            document.setDocumentInformation(information);
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(pageText);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 PDF 测试文档", exception);
        }
    }

    public static byte[] docx(String title, List<String> paragraphs) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getProperties().getCoreProperties().setTitle(title);
            for (String text : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(text);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 DOCX 测试文档", exception);
        }
    }

    /**
     * 生成使用 .doc 扩展名流通的 RTF 内容。
     *
     * <p>该样例验证解析器依据内容而不是客户端 MIME 声明选择格式；Tika 的同一 Office 模块
     * 还负责实际 OLE2 Word 二进制文档解析。</p>
     */
    public static byte[] wordCompatibleDoc(String text) {
        String escaped = text.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}");
        String rtf = "{\\rtf1\\ansi\\deff0"
                + "{\\fonttbl{\\f0 Arial;}}"
                + "\\viewkind4\\uc1\\pard\\f0\\fs24 "
                + escaped
                + "\\par}";
        return rtf.getBytes(StandardCharsets.US_ASCII);
    }
}
