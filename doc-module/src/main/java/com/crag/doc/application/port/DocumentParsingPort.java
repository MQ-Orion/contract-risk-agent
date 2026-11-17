package com.crag.doc.application.port;

import com.crag.doc.domain.model.DocumentFormatEnum;
import com.crag.doc.domain.model.ParsedDocument;
import java.nio.file.Path;

/**
 * 文档解析端口：基础设施实现，负责把文件解析为文本、页数与表格（规范 5.2）。
 */
public interface DocumentParsingPort {

    /**
     * 解析文档。
     *
     * @param file   文件
     * @param format 已校验的格式
     * @return 解析结果
     */
    ParsedDocument parse(Path file, DocumentFormatEnum format);
}
