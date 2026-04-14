package com.mediaservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediaservice.model.Room;
import com.mediaservice.model.UserSession;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KurentoClient kurentoClient;
    private final RecordingService recordingService;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomService(KurentoClient kurentoClient,
                       RecordingService recordingService) {
        this.kurentoClient = kurentoClient;
        this.recordingService = recordingService;
    }

    // ── JOIN ──────────────────────────────────────────
    public String joinRoom(String roomId, UserSession newUser) throws IOException {

        // Xona yo'q bo'lsa yaratish
        rooms.putIfAbsent(roomId, createRoom(roomId));
        Room room = rooms.get(roomId);

        // Kurento WebRTC endpoint yaratish
        WebRtcEndpoint endpoint = new WebRtcEndpoint
                .Builder(room.getPipeline()).build();
        newUser.setWebRtcEndpoint(endpoint);

        // ICE candidate topilsa browserga yuborish
        endpoint.addIceCandidateFoundListener(event -> {
            ObjectNode msg = objectMapper.createObjectNode();

            msg.put("type", "iceCandidate");
            msg.put("candidate", event.getCandidate().getCandidate());
            msg.put("sdpMid", event.getCandidate().getSdpMid());
            msg.put("sdpMLineIndex", event.getCandidate().getSdpMLineIndex());

            try {
                newUser.sendMessage(msg.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Xonaga qo'shish
        room.addParticipant(newUser);

        // Mavjud ishtirokchilarga ulash
        for (UserSession existing : room.getParticipants()) {
            if (!existing.getUsername().equals(newUser.getUsername())) {
                // existing → newUser
                existing.getWebRtcEndpoint()
                        .connect(newUser.getWebRtcEndpoint());
                // newUser → existing
                newUser.getWebRtcEndpoint()
                        .connect(existing.getWebRtcEndpoint());
            }
        }

        if (recordingService.isRecording(roomId)) {
            recordingService.addParticipant(room, newUser);
        }

        // 2 ta bo'lsa yozishni boshlash
        if (room.size() == 2) {
            recordingService.startRecording(room);
        }

        return roomId;
    }

    // ── SDP OFFER QAYTA ISHLASH ───────────────────────
    public String processOffer(String roomId, String username,
                               String sdpOffer) {
        Room room = rooms.get(roomId);
        UserSession user = room.getParticipant(username);

        // Kurento SDP answer qaytaradi
        String sdpAnswer = user.getWebRtcEndpoint().processOffer(sdpOffer);
        user.getWebRtcEndpoint().gatherCandidates();
        return sdpAnswer;
    }

    // ── ICE CANDIDATE ─────────────────────────────────
    public void addIceCandidate(String roomId, String username,
                                String candidate, String sdpMid,
                                int sdpMLineIndex) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }

        Room room = rooms.get(roomId);
        UserSession user = room.getParticipant(username);
        user.addIceCandidate(
                new IceCandidate(candidate, sdpMid, sdpMLineIndex)
        );
    }

    // ── LEAVE ─────────────────────────────────────────
    public void leaveRoom(String roomId, String username) throws IOException {
        Room room = rooms.get(roomId);
        if (room == null) return;

        UserSession leavingUser = room.getParticipant(username);
        room.removeParticipant(username);

        if (leavingUser != null && leavingUser.getWebRtcEndpoint() != null) {
            leavingUser.getWebRtcEndpoint().release();
        }

        // Qolganlarga xabar
        for (UserSession other : room.getParticipants()) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "peer-left");
            msg.put("username", username);
            other.sendMessage(msg.toString());
        }

        if (room.size() < 2) {
            recordingService.stopRecording(roomId);
        }

        // Xona bo'sh → yozishni to'xtatish + xonani yopish
        if (room.size() == 0) {
            room.close();
            rooms.remove(roomId);
        }
    }

    private Room createRoom(String roomId) {
        MediaPipeline pipeline = kurentoClient.createMediaPipeline();
        return new Room(roomId, pipeline);
    }
}
