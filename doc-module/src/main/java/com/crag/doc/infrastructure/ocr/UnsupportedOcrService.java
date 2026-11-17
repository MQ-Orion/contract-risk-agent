package com.crag.doc.infrastructure.ocr;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import com.crag.doc.application.port.OcrService;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OCR 占位实现（规范 5.2）：签名固定、暂不实现，调用即返回“请上传可编辑版本”。
 *
 * <p>将来接入 OCR 只替换本实现，调用方零改动；禁止以“预留”为理由修改接口签名。</p>
 */
public class UnsupportedOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(UnsupportedOcrService.class);

    @Override
    public List<OcrBlock> recognize(InputStream inputStream) {
        log.info("OCR 接口被调用，当前未实现，按规范 5.2 返回不支持");
        throw BizException.of(ErrorCode.OCR_UNSUPPORTED, "ocr not implemented");
    }
}
