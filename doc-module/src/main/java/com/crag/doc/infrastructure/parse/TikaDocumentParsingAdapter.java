package com.crag.doc.infrastructure.parse;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.DocumentParsingPort;
import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.domain.model.ParsedDocument;
import com.crag.doc.domain.model.ParsedTable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * 基于 Apache Tika 的文档解析（规范 5.2）：统一输出文本、页数与表格。
 *
 * <p>表格不采用 Tika 的表格输出：DOCX 由 POI 按 gridSpan/vMerge 还原网格，PDF 由文本坐标重建行列；
 * DOC 暂不做表格还原（其行列表现在二进制流中提取成本高），按无表格处理并记录告警。</p>
 */
@Component
public class TikaDocumentParsingAdapter implements DocumentParsingPort {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentParsingAdapter.class);

    private final DocxTableExtractor docxTableExtractor;
    private final PdfTableExtractor pdfTableExtractor;

    /**
     * 构造解析适配器。
     *
     * @param docxTableExtractor DOCX 表格抽取器
     * @param pdfTableExtractor  PDF 表格抽取器
     */
    public TikaDocumentParsingAdapter(DocxTableExtractor docxTableExtractor, PdfTableExtractor pdfTableExtractor) {
        this.docxTableExtractor = docxTableExtractor;
        this.pdfTableExtractor = pdfTableExtractor;
    }

    @Override
    public ParsedDocument parse(Path file, DocumentFormatEnum format) {
        String text = extractText(file);
        List<ParsedTable> tables = new ArrayList<>();
        int pageCount = 0;
        boolean pageCountKnown = false;
        switch (format) {
            case PDF -> {
                pageCount = pdfTableExtractor.pageCount(file);
                pageCountKnown = pageCount > 0;
                tables.addAll(pdfTableExtractor.extract(file));
            }
            case DOCX -> {
                // DOCX 的页数只在渲染后写入 docProps/app.xml，且不同编辑器默认值不同；
                // 取不到可信值时不冒充已知，页数保持 0 并置 known=false（见 ADR 0002 偏差 11）
                pageCount = 0;
                pageCountKnown = false;
                tables.addAll(docxTableExtractor.extract(file, 0));
            }
            case DOC -> log.info("DOC 文档暂不还原表格（规范 5.2 明确禁止使用 Tika 表格输出），按无表格处理 file={}",
                    file.getFileName());
            default -> {
            }
        }
        return new ParsedDocument(text, pageCount, pageCountKnown, format, tables,
                Map.of("parser", "tika", "tableCount", String.valueOf(tables.size())));
    }

    private String extractText(Path file) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        try (InputStream in = Files.newInputStream(file)) {
            parser.parse(in, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (IOException | SAXException | TikaException e) {
            throw BizException.of(ErrorCode.PARSE_FAILED, "tika parse failed: " + e.getClass().getSimpleName());
        }
    }
}
