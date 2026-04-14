package com.mediaservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediaservice.model.UserSession;
import com.mediaservice.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

                ObjectNode ready = objectMapper.createObjectNode();
                ready.put("type", "ready");

                ws.sendMessage(new TextMessage(ready.toString()));
            }

            case "offer" -> {
                String sdpOffer = json.path("sdp").asText();

                String sdpAnswer = roomService.processOffer(
                        roomId,
                        username,
                        sdpOffer
                );

                ObjectNode answer = objectMapper.createObjectNode();
                answer.put("type", "answer");
                answer.put("sdp", sdpAnswer);

                ws.sendMessage(new TextMessage(answer.toString()));
            }

            case "ice" -> {
                roomService.addIceCandidate(
                        roomId,
                        username,
                        json.path("candidate").asText(),
                        json.path("sdpMid").asText(),
                        json.path("sdpMLineIndex").asInt()
                );
            }

            default -> {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("type", "error");
                error.put("message", "Unsupported message type: " + type);

                ws.sendMessage(new TextMessage(error.toString()));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws,
                                      CloseStatus status) throws Exception {
        UserSession user = sessions.remove(ws.getId());
        if (user != null) {
            roomService.leaveRoom(user.getRoomId(), user.getUsername());
        }
    }
}
