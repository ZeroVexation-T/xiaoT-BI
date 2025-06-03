package com.tang.springbootinit.manager;

import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgent;
import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgentOptions;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.tang.springbootinit.common.ErrorCode;
import com.tang.springbootinit.exception.BusinessException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
/**
 * 用于对接 AI 平台
 * AI调用方法：Spring AI Alibaba + AI应用
 */
@Service
public class AiManager {

    private final DashScopeAgent agent;

    @Value("${spring.ai.dashscope.agent.app-id}")
    private String appId;

    public AiManager(DashScopeAgentApi dashscopeAgentApi) {
        this.agent = new DashScopeAgent(dashscopeAgentApi);
    }
    /**
     * AI 对话
     *
     * @param message
     * @return AI助手生成的文本内容
     */
    public String doChat(@RequestParam(value = "message") String message) {
        // 调用agent的call方法，传入用户消息和配置选项
        // DashScopeAgentOptions指定应用ID，用于标识调用的应用
        ChatResponse response = agent.call(new Prompt(message,
                DashScopeAgentOptions.builder().withAppId(appId).build()));

        // 从响应结果中获取AI助手生成的消息内容
        // getResult()返回调用结果，getOutput()获取输出消息
        AssistantMessage app_output = response.getResult().getOutput();

        if (app_output == null) {
            // 如果AI助手没有返回输出消息，则抛出异常
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 响应错误");
        }

        // 返回AI助手生成的文本内容
        return app_output.getContent();

    }
}
