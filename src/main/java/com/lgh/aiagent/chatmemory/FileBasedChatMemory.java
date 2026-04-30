package com.lgh.aiagent.chatmemory;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.model.Content;
import org.springframework.ai.model.Media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于文件持久化的对话记忆
 */
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;

    // 为每个 conversationId 添加锁，避免并发写入同一文件
    private final Map<String, Object> conversationLocks = new ConcurrentHashMap<>();

    // 每个线程独立的 Kryo 实例
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 注册消息类型
        kryo.register(UserMessage.class);
        kryo.register(AssistantMessage.class);
        kryo.register(SystemMessage.class);
        kryo.register(ToolResponseMessage.class);
        kryo.register(Message.class);

// 注册嵌套类型（关键！）
        kryo.register(Media.class);      // UserMessage.media 字段的类型
        kryo.register(Content.class);    // 内容类型

        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, Message message) {
        if (message == null) {
            return;
        }
        synchronized (getLockForConversation(conversationId)) {
            List<Message> messages = loadMessages(conversationId);
            List<Message> mutableMessages = new ArrayList<>(messages);
            mutableMessages.add(message);
            saveMessages(conversationId, mutableMessages);
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        synchronized (getLockForConversation(conversationId)) {
            List<Message> existingMessages = loadMessages(conversationId);
            List<Message> mutableMessages = new ArrayList<>(existingMessages);
            messages.stream()
                    .filter(Objects::nonNull)
                    .forEach(mutableMessages::add);
            saveMessages(conversationId, mutableMessages);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = loadMessages(conversationId);
        int size = allMessages.size();
        int fromIndex = Math.max(0, size - lastN);
        return allMessages.subList(fromIndex, size).stream()
                .filter(msg -> msg != null && isValidMessage(msg))
                .toList();
    }

    @Override
    public void clear(String conversationId) {
        synchronized (getLockForConversation(conversationId)) {
            File file = getConversationFile(conversationId);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private List<Message> loadMessages(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        Kryo kryo = kryoThreadLocal.get();
        try (Input input = new Input(new FileInputStream(file))) {
            Object obj = kryo.readClassAndObject(input);
            if (!(obj instanceof List)) {
                deleteCorruptedFile(file);
                return new ArrayList<>();
            }

            List<?> rawList = (List<?>) obj;
            return rawList.stream()
                    .filter(item -> item instanceof Message)
                    .map(item -> (Message) item)
                    .filter(msg -> msg != null && isValidMessage(msg))
                    .toList();
        } catch (Exception e) {
            System.err.println("Read failed: " + file.getName() + ", error: " + e.getMessage());
            deleteCorruptedFile(file);
            return new ArrayList<>();
        }
    }

    private void saveMessages(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        List<Message> cleanMessages = messages.stream()
                .filter(Objects::nonNull)
                .filter(this::isValidMessage)
                .toList();

        if (cleanMessages.isEmpty()) {
            if (file.exists()) {
                file.delete();
            }
            return;
        }

        Kryo kryo = kryoThreadLocal.get();
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeClassAndObject(output, cleanMessages);
        } catch (Exception e) {
            System.err.println("Write failed: " + file.getName() + ", error: " + e.getMessage());
            deleteCorruptedFile(file);
        }
    }

    private boolean isValidMessage(Message msg) {
        if (msg instanceof org.springframework.ai.chat.messages.UserMessage userMsg) {
            return userMsg.getText() != null && !userMsg.getText().isEmpty();
        } else if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage assistantMsg) {
            return assistantMsg.getText() != null && !assistantMsg.getText().isEmpty();
        }
        return true;
    }

    private void deleteCorruptedFile(File file) {
        try {
            if (file.delete()) {
                System.out.println("Deleted corrupted: " + file.getName());
            }
        } catch (Exception e) {
            System.err.println("Delete failed: " + e.getMessage());
        }
    }

    private Object getLockForConversation(String conversationId) {
        return conversationLocks.computeIfAbsent(conversationId, k -> new Object());
    }

    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
