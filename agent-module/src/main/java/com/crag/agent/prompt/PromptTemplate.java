package com.crag.agent.prompt;

import java.util.List;

/**
 * Prompt 模板（规范 7.3：外置为带版本号的文件，禁止字符串内联拼接）。
 *
 * @param name         模板名，对应文件名前缀
 * @param version      版本号，来自文件头 front matter
 * @param systemPrompt 系统提示正文
 */
public record PromptTemplate(String name, String version, String systemPrompt) {

    /**
     * 四项固定内容的最小校验（规范 7.3）：角色与业务边界、禁止编造、必须引用切片、输出结构。
     * 缺任一项都在启动期失败，避免上线后才发现提示词没写全。
     *
     * @return 缺失项列表；完整返回空列表
     */
    public List<String> missingRequiredSections() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (!systemPrompt.contains("角色与业务边界")) {
            missing.add("角色与业务边界");
        }
        if (!systemPrompt.contains("禁止编造")) {
            missing.add("禁止编造");
        }
        if (!systemPrompt.contains("必须引用切片标识")) {
            missing.add("必须引用切片标识");
        }
        if (!systemPrompt.contains("输出结构")) {
            missing.add("输出结构");
        }
        return List.copyOf(missing);
    }
}
