package com.mediaservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediaservice.model.Room;
import com.mediaservice.model.UserSession;
import com.mediaservice.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Component
public class CallController extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomService roomService;
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession ws,
                                     TextMessage message) throws Exception {

        JsonNode json = objectMapper.readTree(message.getPayload());
        String type = json.path("type").asText();
        String username = json.path("username").asText();
        String roomId = json.path("room").asText();

        switch (type) {
            case "join" -> {
                UserSession user = new UserSession(username, roomId, ws);
                sessions.put(ws.getId(), user);
                roomService.joinRoom(roomId, user);

                // ✅ ws.sendMessage() emas, user.sendMessage() orqali
                ObjectNode ready = objectMapper.createObjectNode();
                ready.put("type", "ready");
//                ready.put("room", objectMapper.writeValueAsString(room));
                user.sendMessage(ready.toString());
            }

            case "offer" -> {
                String sdpOffer = json.path("sdp").asText();
                UserSession user = sessions.get(ws.getId());
                if (user == null) {
                    log.warn("offer: session topilmadi, wsId={}", ws.getId());
                    return;
                }

                String sdpAnswer = roomService.processOffer(roomId, username, sdpOffer);

                // ✅ ws.sendMessage() emas, user.sendMessage() orqali
                ObjectNode answer = objectMapper.createObjectNode();
                answer.put("type", "answer");
                answer.put("sdp", sdpAnswer);
                user.sendMessage(answer.toString());
            }

            case "ice" -> {
                // ✅ NullPointer oldini olish
                UserSession user = sessions.get(ws.getId());
                if (user == null) {
                    log.warn("ice: session topilmadi, wsId={}", ws.getId());
                    return;
                }
                String candidate = json.path("candidate").asText();
                if (candidate.isBlank()) return;

                roomService.addIceCandidate(
                        roomId, username,
                        candidate,
                        json.path("sdpMid").asText(),
                        json.path("sdpMLineIndex").asInt()
                );
            }

            default -> {
                UserSession user = sessions.get(ws.getId());
                ObjectNode error = objectMapper.createObjectNode();
                error.put("type", "error");
                error.put("message", "Unsupported message type: " + type);
                if (user != null) {
                    user.sendMessage(error.toString());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws,
                                      CloseStatus status) throws Exception {
        UserSession user = sessions.remove(ws.getId());
        if (user != null) {
            user.close(); // ✅ sender thread'ni to'xtatish
            roomService.leaveRoom(user.getRoomId(), user.getUsername());
        }
    }
}
