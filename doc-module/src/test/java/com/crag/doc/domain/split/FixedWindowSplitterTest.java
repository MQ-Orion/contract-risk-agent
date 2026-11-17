package com.crag.doc.domain.split;

import com.crag.common.text.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 固定窗口切分器测试：该实现只服务切分策略对比（规范 12.2）。
 */
class FixedWindowSplitterTest {

    @Test
    @DisplayName("按 800 字窗口、10% 重叠切分")
    void splitsWithOverlap() {
        String text = "合".repeat(2000);
        FixedWindowSplitter splitter = new FixedWindowSplitter(SplitConfig.defaults());

        List<FixedWindowSplitter.Window> windows = splitter.split(text);

        assertThat(windows).hasSize(3);
        assertThat(windows.get(0).text()).hasSize(800);
        assertThat(windows.get(1).start() - windows.get(0).start()).isEqualTo(720);
        assertThat(windows.get(0).end() - windows.get(1).start()).isEqualTo(80);
        assertThat(windows.get(2).end()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("短文本只有一个窗口")
    void singleWindowForShortText() {
        List<FixedWindowSplitter.Window> windows = new FixedWindowSplitter(SplitConfig.defaults()).split("短合同");

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).text()).isEqualTo("短合同");
    }

    @Test
    @DisplayName("空文本返回空窗口")
    void emptyText() {
        assertThat(new FixedWindowSplitter(SplitConfig.defaults()).split(" ")).isEmpty();
    }
}
