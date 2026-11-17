package com.crag.agent.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Prompt 注册中心（规范 7.3）：启动时加载 {@code classpath:prompts/*.md}，解析版本号并校验四项固定内容。
 *
 * <p>为什么在启动期校验而不是首次调用时：Prompt 缺“禁止编造”或“必须引用切片”属于配置缺陷，
 * 让它停在启动阶段比停在生产审核阶段便宜得多（规范 7.4 同理）。</p>
 *
 * <p>线程安全性：构造后只读，可共享。</p>
 */
public class PromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);

    private final Map<String, PromptTemplate> templates;

    /**
     * 构造注册中心。
     *
     * @param locationPattern 资源匹配表达式，默认 {@code classpath:prompts/*.md}
     */
    public PromptRegistry(String locationPattern) {
        this.templates = load(locationPattern);
        if (templates.isEmpty()) {
            throw new IllegalStateException("未找到任何 Prompt 文件：" + locationPattern);
        }
    }

    /**
     * 按名称获取模板。
     *
     * @param name 模板名
     * @return 模板
     */
    public PromptTemplate get(String name) {
        PromptTemplate template = templates.get(name);
        if (template == null) {
            throw new IllegalStateException("Prompt 未注册：" + name);
        }
        return template;
    }

    /**
     * 全部模板。
     *
     * @return 模板列表
     */
    public List<PromptTemplate> all() {
        return List.copyOf(templates.values());
    }

    private Map<String, PromptTemplate> load(String locationPattern) {
        Map<String, PromptTemplate> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            for (Resource resource : resources) {
                PromptTemplate template = parse(resource);
                List<String> missing = template.missingRequiredSections();
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("Prompt " + template.name() + " 缺少固定内容：" + missing);
                }
                loaded.put(template.name(), template);
                log.info("Prompt 已加载 name={} version={}", template.name(), template.version());
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载 Prompt 失败", e);
        }
        return loaded;
    }

    private PromptTemplate parse(Resource resource) throws IOException {
        String text;
        try (InputStream in = resource.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String name = null;
        String version = null;
        String body = text;
        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                String header = text.substring(3, end);
                body = text.substring(end + 4).strip();
                for (String line : header.split("\n")) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith("name:")) {
                        name = trimmed.substring("name:".length()).strip();
                    } else if (trimmed.startsWith("prompt-version:")) {
                        version = trimmed.substring("prompt-version:".length()).strip();
                    }
                }
            }
        }
        if (name == null || name.isBlank() || version == null || version.isBlank()) {
            throw new IllegalStateException("Prompt 文件缺少 name 或 prompt-version：" + resource.getFilename());
        }
        return new PromptTemplate(name, version, body);
    }
}
