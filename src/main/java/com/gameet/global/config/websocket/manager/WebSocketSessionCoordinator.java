package com.gameet.global.config.websocket.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class WebSocketSessionCoordinator {

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final WebSocketSessionCloser webSocketSessionCloser;

    public void registerSession(WebSocketSession session) {
        webSocketSessionRegistry.register(session);
    }

    public void closeSession(WebSocketSession session) {
        webSocketSessionRegistry.unregisterSession(session);
        webSocketSessionCloser.tryCloseSession(session, CloseStatus.NORMAL);
    }

    public void closeSessionsOnLogout(Long userId) {
        webSocketSessionRegistry.closeSessionsOnLogout(userId);
    }

    public boolean hasSession(String tabId) {
        return webSocketSessionRegistry.hasSession(tabId);
    }
}
