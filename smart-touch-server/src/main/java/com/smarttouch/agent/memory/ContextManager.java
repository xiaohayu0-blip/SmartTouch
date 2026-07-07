package com.smarttouch.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上下文管理器
 * 在有限token窗口内保留关键信息，平衡信息完整性与API成本
 *
 * 策略：
 * - 始终保留：系统提示词 + 用户原始指令
 * - 最近N步保留完整记录（含截图base64）
 * - 早期步骤做摘要压缩（仅保留动作+结果的一句话描述）
 * - 截图只保留最近2帧
 */
@Slf4j
@Component
public class ContextManager {

    /** 存储每步的执行记录（按步骤序号排序） */
    private final LinkedHashMap<Integer, StepRecord> stepRecords = new LinkedHashMap<>();

    /** 用户原始指令 */
    private String userInstruction;

    /** 当前策略 */
    private MemoryStrategy strategy = MemoryStrategy.defaultStrategy();

    /** 重置上下文（新任务开始时调用） */
    public void reset(String instruction, MemoryStrategy memoryStrategy) {
        this.stepRecords.clear();
        this.userInstruction = instruction;
        this.strategy = memoryStrategy != null ? memoryStrategy : MemoryStrategy.defaultStrategy();
    }

    /** 添加一步执行记录 */
    public void addStep(int stepNo, String action, Map<String, Object> params,
                        String result, String reason, String screenshotBase64, int costMs) {
        StepRecord record = StepRecord.builder()
                .stepNo(stepNo)
                .action(action)
                .params(params)
                .result(result)
                .reason(reason)
                .screenshotBase64(screenshotBase64)
                .costMs(costMs)
                .build();
        stepRecords.put(stepNo, record);
    }

    /** 构建发送给LLM的上下文文本（纯文本部分，截图另外处理） */
    public String buildTextContext() {
        StringBuilder sb = new StringBuilder();

        // 用户原始指令
        sb.append("【用户指令】").append(userInstruction).append("\n\n");

        // 历史步骤摘要
        List<Integer> stepNos = new ArrayList<>(stepRecords.keySet());
        if (!stepNos.isEmpty()) {
            sb.append("【已执行步骤】\n");
            int recentN = strategy.getRecentSteps();

            for (int i = 0; i < stepNos.size(); i++) {
                int stepNo = stepNos.get(i);
                StepRecord record = stepRecords.get(stepNo);

                // 最近N步保留完整信息，早期步骤做摘要
                if (i >= stepNos.size() - recentN) {
                    sb.append(formatFullStep(record));
                } else {
                    sb.append(formatSummaryStep(record));
                }
            }
            sb.append("\n");
        }

        sb.append("请根据当前截图决定下一步操作，仅输出JSON。");
        return sb.toString();
    }

    /** 获取最近N帧截图（用于多模态模型输入） */
    public List<String> getRecentScreenshots() {
        int maxScreenshots = strategy.getRecentScreenshots();
        List<Integer> stepNos = new ArrayList<>(stepRecords.keySet());

        return stepNos.stream()
                .skip(Math.max(0, stepNos.size() - maxScreenshots))
                .map(stepRecords::get)
                .filter(r -> r.getScreenshotBase64() != null && !r.getScreenshotBase64().isEmpty())
                .map(StepRecord::getScreenshotBase64)
                .collect(Collectors.toList());
    }

    /** 获取最近一步的截图（LLM需要根据最新截图决策下一步） */
    public String getLatestScreenshot() {
        if (stepRecords.isEmpty()) return null;
        List<Integer> stepNos = new ArrayList<>(stepRecords.keySet());
        // 倒序查找最近的有效截图
        for (int i = stepNos.size() - 1; i >= 0; i--) {
            StepRecord record = stepRecords.get(stepNos.get(i));
            if (record.getScreenshotBase64() != null && !record.getScreenshotBase64().isEmpty()) {
                return record.getScreenshotBase64();
            }
        }
        return null;
    }

    /** 获取当前总步数 */
    public int getStepCount() {
        return stepRecords.size();
    }

    /** 获取最近N步的摘要（用于日志和调试） */
    public String getRecentSummary(int n) {
        List<Integer> stepNos = new ArrayList<>(stepRecords.keySet());
        int start = Math.max(0, stepNos.size() - n);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < stepNos.size(); i++) {
            sb.append(formatSummaryStep(stepRecords.get(stepNos.get(i))));
        }
        return sb.toString();
    }

    /** 格式化完整步骤记录 */
    private String formatFullStep(StepRecord record) {
        return String.format("Step[%d]: %s(%s) → %s | %s | 耗时%dms\n",
                record.getStepNo(), record.getAction(),
                record.getParams(), record.getResult(),
                record.getReason(), record.getCostMs());
    }

    /** 格式化摘要步骤记录（只保留一行描述） */
    private String formatSummaryStep(StepRecord record) {
        return String.format("Step[%d]: %s → %s\n",
                record.getStepNo(), record.getAction(), record.getResult());
    }

    // ==================== 内部类 ====================

    @lombok.Data
    @lombok.Builder
    public static class StepRecord {
        private int stepNo;
        private String action;
        private Map<String, Object> params;
        private String result;
        private String reason;
        private String screenshotBase64;
        private int costMs;
    }
}
