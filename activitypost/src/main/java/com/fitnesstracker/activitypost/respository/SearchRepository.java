package com.fitnesstracker.activitypost.respository;
import java.util.List;
import com.fitnesstracker.activitypost.model.Post;

public interface SearchRepository {

    List<Post> findByText(String text);

}
