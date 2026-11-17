package com.fitnesstracker.journal.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class AppConfig {

    /** Used for "today" in stats and demo data, so tests can pin the date. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Store LocalDate/LocalTime with the driver's own codecs. The Spring default goes through
     * java.util.Date in the JVM time zone, which can shift dates by a day between machines.
     */
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(c -> c.useNativeDriverJavaTimeCodecs());
    }
}
