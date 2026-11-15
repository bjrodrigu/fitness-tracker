package com.fitnesstracker.activitypost.respository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.fitnesstracker.activitypost.model.Post;

public interface PostRepository extends MongoRepository<Post, String> {
    
}
