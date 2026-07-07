package com.smarttouch.agent.memory;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆策略配置
 * 控制上下文窗口的大小和摘要压缩行为
 */
@Data
@Builder
public class MemoryStrategy {

    /** 上下文保留最近N步的完整记录 */
    @Builder.Default
    private int recentSteps = 10;

    /** 只保留最近M帧截图（减少token消耗） */
    @Builder.Default
    private int recentScreenshots = 2;

    /** 超过该步数后对早期步骤做更激进的摘要压缩 */
    @Builder.Default
    private int compressThreshold = 15;

    /** 默认策略 */
    public static MemoryStrategy defaultStrategy() {
        return MemoryStrategy.builder().build();
    }

    /** 长任务策略（超过20步时更激进裁剪） */
    public static MemoryStrategy longTaskStrategy() {
        return MemoryStrategy.builder()
                .recentSteps(5)
                .recentScreenshots(1)
                .compressThreshold(10)
                .build();
    }
}
