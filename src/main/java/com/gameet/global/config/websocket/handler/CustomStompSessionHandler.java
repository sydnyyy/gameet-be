package com.gameet.global.config.websocket.handler;

import com.gameet.global.config.websocket.interceptor.WebSocketAuthHandshakeInterceptor;
import com.gameet.global.config.websocket.manager.WebSocketSessionCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Slf4j
public class CustomStompSessionHandler extends WebSocketHandlerDecorator {

    private final WebSocketSessionCoordinator webSocketSessionCoordinator;

    public CustomStompSessionHandler(WebSocketHandler delegate,
                                     WebSocketSessionCoordinator webSocketSessionCoordinator) {
        super(delegate);
        this.webSocketSessionCoordinator = webSocketSessionCoordinator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        webSocketSessionCoordinator.registerSession(session);

        String userId = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_KEY).toString();
        String clientId = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.CLIENT_ID_KEY).toString();
        String tabWebSocketToken = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.WEBSOCKET_TOKEN_KEY).toString();
        log.info("🟢 WebSocket 세션 등록. userId={}, clientId={}, sessionId={}, tabWebSocketToken={}", userId, clientId, session.getId(), tabWebSocketToken);

        super.afterConnectionEstablished(session);
    }
}
