package com.fitnesstracker.journal;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;

/** One throwaway MongoDB in Docker, shared by every API test class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = { "app.demo-data=false",
        "app.garmin.schedule=false",
        // Test-only key: 32 zero bytes.
        "app.garmin.token-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
public abstract class ApiTestBase {

    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    static {
        mongo.start();
    }
}
