package com.lgh.imagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    public void testSearchImage() {
        String result = imageSearchTool.searchImage("哄另一半开心的图片");
        Assertions.assertNotNull( result);
    }
}
