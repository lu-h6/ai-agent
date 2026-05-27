package com.lgh.aiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class MultiQueryTest {

    @Resource
    private MultiQuery multiQuery;

    @Test
    public void expend() {
        List<Query> queries = multiQuery.expend();
        Assertions.assertNotNull(queries);
    }
}
