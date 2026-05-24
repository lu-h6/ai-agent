package com.lgh.aiagent.app;

import com.lgh.aiagent.advisor.MyLoggerAdvisor;
import com.lgh.aiagent.chatmemory.FileBasedChatMemory;
import com.lgh.aiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.lgh.aiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoveApp {
    private final ChatClient chatClient;
    // ... existing code ...
    private static final String SYSTEM_PROMPT = "你是恋爱心理专家兼智能助手，具备搜索、下载、地图等工具能力。" +
            "开场向用户表明身份，告知用户可倾诉恋爱难题、获取约会建议或使用实用工具。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "**核心执行规则**：" +
            "1. 【工具优先】当用户明确要求搜索图片/信息、下载资源、查询地点、生成PDF等具体操作时，必须立即调用对应工具执行，禁止先进行心理分析或反问。" +
            "2. 【结果呈现】工具执行后，直接返回结果（如图片URL、文件路径、地点列表），再补充心理学建议。" +
            "3. 【失败处理】若工具调用失败，明确告知用户原因并提供替代方案。" +
            "4. 【情境判断】仅在用户表达情感困惑、关系问题时，才展开心理咨询流程。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";
// ... existing code ...


    public LoveApp(ChatModel dashscopeChatModel) {
        //初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        FileBasedChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        //初始化基于内存的对话记忆
        //ChatMemory chatMemory = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();
    }

    /**
     * AI 基础对话，支持多轮对话记忆
     *
     * @param message
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                //通过 advisors 设置对话 ID（chatId）和检索大小（10 条历史消息），实现上下文关联
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    /**
     * AI 基础对话，支持多轮对话记忆（流式输出）
     *
     * @param message
     * @return
     */

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content()
                .filter(content -> content != null && !content.isEmpty());
    }


    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * Ai 恋爱报告功能，实战结构化输出
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient.prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                //通过 advisors 设置对话 ID（chatId）和检索大小（10 条历史消息），实现上下文关联
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("content:{}", loveReport);
        return loveReport;
    }

    /**
     * AI 恋爱知识库功能
     */
    @Resource
    private VectorStore loveAppVectorStore;
    @Resource
    private QueryRewriter queryRewriter;

    public String doChatWithRag(String message, String chatId) {
        String rewriteMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                //.user(message)
                //使用改写后的message
                .user(rewriteMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                //.advisors(new MyLoggerAdvisor())
                //应用rag
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 用自定义的RAG + 上下文关联
                // 过滤掉与“单身”无关的文档
                //.advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore, "单身"))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    public String doChatWithYunRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)

                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                //.advisors(new MyLoggerAdvisor())
                // 应用增强检索服务（云知识库服务）
                .advisors(loveAppRagCloudAdvisor)
                // 应用增强检索服务（PgVector知识库服务）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                //.advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * mcp集成工具功能
     */
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * mcp集成工具功能
     */
    public String doChatWithMcpTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                //.advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


}
