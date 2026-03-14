package com.example.project.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AttendanceSoapData {

    private String userId;
    private String userName;
    private String timeStamp;
    private String direction;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getTimeStamp() { return timeStamp; }
    public void setTimeStamp(String timeStamp) { this.timeStamp = timeStamp; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    /**
     * Converts "2025-11-25T10:41:50" → LocalDateTime object.
     */
    public LocalDateTime getTimeAsDateTime() {
        return LocalDateTime.parse(timeStamp);
    }

    /**
     * Converts "2025-11-25T10:41:50" → "10:41"
     */
    public String getTimeOnly() {
        LocalDateTime dt = LocalDateTime.parse(timeStamp);
        return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    @Override
    public String toString() {
        return userId + " | " + timeStamp + " | " + direction;
    }
}
