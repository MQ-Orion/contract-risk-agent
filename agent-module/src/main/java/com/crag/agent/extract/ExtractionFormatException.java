package com.crag.agent.extract;

/**
 * 抽取输出格式不合法（规范 7.4：校验失败重试一次，仍失败则降级）。
 *
 * <p>不在此处“尽力解析”：用正则硬抠字段会把格式错误变成数据错误，规范明确禁止。</p>
 */
public class ExtractionFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 原因
     */
    public ExtractionFormatException(String message) {
        super(message);
    }
}
