package com.smarttouch.service;

import com.smarttouch.entity.SessionRecord;
import com.smarttouch.mapper.SessionRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话记录 Service
 * 保存Agent与LLM的完整交互日志
 */
@Slf4j
@Service
public class SessionService {

    private final SessionRecordMapper sessionRecordMapper;

    public SessionService(SessionRecordMapper sessionRecordMapper) {
        this.sessionRecordMapper = sessionRecordMapper;
    }

    /** 保存一条会话记录 */
    public void saveRecord(Long taskId, Integer stepNo, String role, String content, int tokenCount) {
        SessionRecord record = SessionRecord.builder()
                .taskId(taskId)
                .stepNo(stepNo)
                .role(role)
                .content(content)
                .tokenCount(tokenCount)
                .build();
        sessionRecordMapper.insert(record);
    }

    /** 查询某任务的全部交互日志 */
    public List<SessionRecord> getRecordsByTaskId(Long taskId) {
        return sessionRecordMapper.selectByTaskId(taskId);
    }

    /** 查询某任务某步骤的交互日志 */
    public List<SessionRecord> getRecordsByStep(Long taskId, Integer stepNo) {
        return sessionRecordMapper.selectByTaskIdAndStepNo(taskId, stepNo);
    }
}
