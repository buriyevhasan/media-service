package com.mediaservice.service;

import com.mediaservice.model.Room;
import com.mediaservice.model.UserSession;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.RecorderEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecordingService {

    @Value("${recordings.path:/var/recordings/}")
    private String recordingsPath;

    // roomId → RecorderEndpoint
    private final Map<String, RecorderEndpoint> recorders = new ConcurrentHashMap<>();

    public void startRecording(Room room) {
        if (recorders.containsKey(room.getRoomId())) {
            return;
        }

        String filePath = recordingsPath + room.getRoomId() + ".webm";

        RecorderEndpoint recorder = new RecorderEndpoint
                .Builder(room.getPipeline(), "file://" + filePath)
                .withMediaProfile(MediaProfileSpecType.WEBM)
                .build();

        // Barcha ishtirokchilarni recorderga ulash
        for (UserSession user : room.getParticipants()) {
            user.getWebRtcEndpoint().connect(recorder);
        }

        recorder.record();
        recorders.put(room.getRoomId(), recorder);
    }

    public boolean isRecording(String roomId) {
        return recorders.containsKey(roomId);
    }

    public void addParticipant(Room room, UserSession user) {
        RecorderEndpoint recorder = recorders.get(room.getRoomId());
        if (recorder != null) {
            user.getWebRtcEndpoint().connect(recorder);
        }
    }

    public void stopRecording(String roomId) {
        RecorderEndpoint recorder = recorders.remove(roomId);
        if (recorder != null) {
            recorder.stop();
            recorder.release();
        }
    }
}
