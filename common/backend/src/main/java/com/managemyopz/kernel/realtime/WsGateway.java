/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kernel WebSocket gateway for /ws/opzhub (doc 04 §7). Feature modules never
 * subscribe directly — they call NotifyPort, which is not implemented yet
 * since no application module is packed. Heartbeats keep Nginx idle timeouts
 * from dropping the socket (doc 09 §4.2).
 */
@Component
public class WsGateway extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"heartbeat\",\"ts\":\"" + Instant.now() + "\"}"));
        }
    }

    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        sessions.values().removeIf(session -> {
            if (!session.isOpen()) {
                return true;
            }
            try {
                session.sendMessage(message);
                return false;
            } catch (IOException exception) {
                return true;
            }
        });
    }
}
