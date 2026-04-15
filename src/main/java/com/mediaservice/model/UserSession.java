package com.mediaservice.model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
@Setter
public class UserSession {
    private final String username;
    private final String roomId;
    private final WebSocketSession wsSession;
    private WebRtcEndpoint webRtcEndpoint;

    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private final Thread senderThread;

    public UserSession(String username, String roomId, WebSocketSession wsSession) {
        this.username = username;
        this.roomId = roomId;
        this.wsSession = wsSession;

        this.senderThread = new Thread(() -> {
            while (running || !messageQueue.isEmpty()) {
                try {
                    String msg = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (msg != null && wsSession.isOpen()) {
                        wsSession.sendMessage(new TextMessage(msg));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Xabar yuborishda xato [{}]: {}", username, e.getMessage());
                }
            }
        }, "ws-sender-" + username);

        this.senderThread.setDaemon(true);
        this.senderThread.start();
    }

    // Barcha threadlardan xavfsiz — to'g'ridan-to'g'ri ws ga YOZMA
    public void sendMessage(String message) {
        if (wsSession.isOpen()) {
            messageQueue.offer(message);
        }
    }

    public void close() {
        running = false;
        senderThread.interrupt();
    }

    public void addIceCandidate(IceCandidate candidate) {
        if (webRtcEndpoint != null) {
            webRtcEndpoint.addIceCandidate(candidate);
        }
    }
}
