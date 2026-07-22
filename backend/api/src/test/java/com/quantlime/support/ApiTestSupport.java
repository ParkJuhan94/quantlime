package com.quantlime.support;

import com.quantlime.QuantLimeApplication;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(classes = QuantLimeApplication.class)
@AutoConfigureMockMvc
public abstract class ApiTestSupport extends TestContainerSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }
}
