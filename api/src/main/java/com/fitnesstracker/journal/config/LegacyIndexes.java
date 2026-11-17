package com.fitnesstracker.journal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.fitnesstracker.journal.activity.Activity;

/**
 * Before accounts, externalId was unique across the whole collection. Now it is unique per
 * account (see Activity), so the old index is dropped if it's still there.
 */
@Component
@Order(0)
class LegacyIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyIndexes.class);
    private final MongoTemplate mongo;

    LegacyIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        var ops = mongo.indexOps(Activity.class);
        if (ops.getIndexInfo().stream().anyMatch(i -> i.getName().equals("externalId"))) {
            ops.dropIndex("externalId");
            log.info("Dropped the old global externalId index.");
        }
    }
}
