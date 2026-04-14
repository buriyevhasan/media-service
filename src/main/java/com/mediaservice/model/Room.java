package com.mediaservice.model;

import lombok.Getter;
import org.kurento.client.MediaPipeline;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Room {
    private final String roomId;
    private final MediaPipeline pipeline;      // Kurento pipeline
    private final Map<String, UserSession> participants = new ConcurrentHashMap<>();

    public Room(String roomId, MediaPipeline pipeline) {
        this.roomId = roomId;
        this.pipeline = pipeline;
    }

    public void addParticipant(UserSession session) {
        participants.put(session.getUsername(), session);
    }

    public void removeParticipant(String username) {
        participants.remove(username);
    }

    public Collection<UserSession> getParticipants() {
        return participants.values();
    }

    public UserSession getParticipant(String username) {
        return participants.get(username);
    }

    public int size() {
        return participants.size();
    }

    // Xona yopilganda Kurento resurslarini tozalash
    public void close() {
        pipeline.release();
    }
}
