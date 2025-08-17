package com.gameet.global.dto.websocket;

import com.gameet.global.config.websocket.interceptor.WebSocketAuthHandshakeInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.WebSocketSession;

public record WebSocketSessionInfo (
        String userId,
        String clientId,
        String tabId,
        String sessionId,
        String tabWebSocketToken
) {
    public static WebSocketSessionInfo of(WebSocketSession session) {
        return new WebSocketSessionInfo(
                session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_KEY).toString(),
                session.getAttributes().get(WebSocketAuthHandshakeInterceptor.CLIENT_ID_KEY).toString(),
                session.getAttributes().get(WebSocketAuthHandshakeInterceptor.TAB_ID_KEY).toString(),
                session.getId(),
                session.getAttributes().get(WebSocketAuthHandshakeInterceptor.WEBSOCKET_TOKEN_KEY).toString()
        );
    }

    @NotNull
    @Override
    public String toString() {
        return String.format("userId=%s, clientId=%s, tabId=%s, sessionId=%s, token=%s",
                userId, clientId, tabId, sessionId, tabWebSocketToken);
    }
}
