package com.fitnesstracker.activitypost.model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ActivityPost")
public class Post {
    private String activityName;
    private String date;
    private String time;
    private float distance;
    private String duration;
    private String pace;
    
    public Post() {
    }
    
    @Override
    public String toString() {
        return "Post [activityName=" + activityName + ", date=" + date + ", time=" + time + ", distance=" + distance
                + ", duration=" + duration + ", pace=" + pace + "]";
    }

    public String getActivityName() {
        return activityName;
    }
    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }
    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }
    public String getTime() {
        return time;
    }
    public void setTime(String time) {
        this.time = time;
    }
    public float getDistance() {
        return distance;
    }
    public void setDistance(float distance) {
        this.distance = distance;
    }
    public String getDuration() {
        return duration;
    }
    public void setDuration(String duration) {
        this.duration = duration;
    }
    public String getPace() {
        return pace;
    }
    public void setPace(String pace) {
        this.pace = pace;
    }
}
