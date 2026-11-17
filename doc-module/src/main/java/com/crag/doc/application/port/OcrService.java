package com.crag.doc.application.port;

import java.io.InputStream;
import java.util.List;

/**
 * OCR 接口（规范 5.2）：签名固定、暂不实现。调用方不得因“预留”而修改签名。
 */
public interface OcrService {

    /**
     * 识别图像化文本。
     *
     * @param inputStream 图片或扫描件输入流
     * @return 文本块
     */
    List<OcrBlock> recognize(InputStream inputStream);

    /**
     * OCR 文本块。
     *
     * @param pageNo 页码
     * @param text   文本
     * @param x      左上角横坐标
     * @param y      左上角纵坐标
     */
    record OcrBlock(int pageNo, String text, float x, float y) {
    }
}
