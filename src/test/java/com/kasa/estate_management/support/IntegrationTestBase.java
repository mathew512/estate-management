package com.kasa.estate_management.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseInitializer.class)
public abstract class IntegrationTestBase {

    @Value("${local.server.port}")
    protected int port;

    protected TestApi api() {
        return new TestApi(port);
    }
}