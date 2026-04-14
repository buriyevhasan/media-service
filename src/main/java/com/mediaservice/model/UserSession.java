package com.mediaservice.model;

import lombok.Getter;
import lombok.Setter;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Getter
@Setter
public class UserSession {
    private final String username;
    private final String roomId;
    private final WebSocketSession wsSession;
    private WebRtcEndpoint webRtcEndpoint;  // Kurento endpoint

    public UserSession(String username, String roomId,
                       WebSocketSession wsSession) {
        this.username = username;
        this.roomId = roomId;
        this.wsSession = wsSession;
    }

    // Browserga xabar yuborish
    public void sendMessage(String message) throws IOException {
        wsSession.sendMessage(new TextMessage(message));
    }

    // ICE candidate qabul qilish
    public void addIceCandidate(IceCandidate candidate) {
        webRtcEndpoint.addIceCandidate(candidate);
    }

}
