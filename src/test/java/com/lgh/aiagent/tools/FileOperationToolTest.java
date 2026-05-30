package com.lgh.aiagent.tools;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.Assert.*;

public class FileOperationToolTest {

    @Test
    public void readFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "什么时快乐星球.md";
        String result = fileOperationTool.readFile(fileName);
        System.out.println(result);
        Assertions.assertNotNull(result);
    }

    @Test
    public void writeFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "什么时快乐星球.md";
        String content = "快乐星球在哪里！！！";
        String result = fileOperationTool.writeFile(fileName, content);
        Assertions.assertNotNull(result);
    }
}