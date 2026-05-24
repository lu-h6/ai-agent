package com.lgh.aiagent.agent;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.lgh.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具调用代理抽象类（具体实现了 think 和 act 方法）
 */

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    //可用的工具
    private final ToolCallback[] availableTools;

    //保存工具调用结果
    private ChatResponse toolCallResponse;

    //工具调用管理
    private final ToolCallingManager toolCallingManager;

    //禁用内置的工具调用机制，自己维护上下文
    private final ChatOptions chatOptions;

    //构造方法
    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        //禁用内置的工具调用机制，自己维护上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }

    @Override
    public boolean think() {
        //校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            //再加入原来的提示
            getMessageList().add(userMessage);
        }
        //调用大模型，获取工具调用后结果
        //将提示词转为prompt
        List<Message> messageList = getMessageList();
        //放入用户提示和系统提示（要求禁用内置工具调用机制）
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        //再把prompt传入大模型，获取结果
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();
            //保存工具调用结果,用于act
            this.toolCallResponse = chatResponse;
            //解析工具调用结果，获取要调用的工具
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            //输出提示信息
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考结果：" + result);
            log.info(getName() + "的调用工具：" + toolCallList);
            //
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name()
                            , toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(getName() + "的调用工具详情：" + toolCallInfo);
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程出错：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            return false;
        }

    }

    @Override
    public String act() {
        if (!toolCallResponse.hasToolCalls()) {
            return "没有工具调用";
        }
        //调用工具
        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallResponse);
        //记录消息上下文
        setMessageList(toolExecutionResult.conversationHistory());
        //当前工具调用的结果
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));
// 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        log.info(results);
        return results;

    }
}
