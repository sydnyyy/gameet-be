package com.gameet.global.config.websocket.manager;

import com.gameet.common.enums.AlertLevel;
import com.gameet.common.service.DiscordNotifier;
import com.gameet.global.config.websocket.interceptor.WebSocketAuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionRegistry {

    private final Map<Long, Set<String>> userClients = new ConcurrentHashMap<>();  // { userId, clientId }
    private final Map<String, Set<String>> clientTabTokens = new ConcurrentHashMap<>();  // { clientId, browserTabToken }
    private final Map<String, WebSocketSession> browserTabSessions = new ConcurrentHashMap<>();  // { browserTabToken, session }
    private final Set<String> closingSessionTokens = ConcurrentHashMap.newKeySet();

    private final WebSocketSessionCloser webSocketSessionCloser;
    private final DiscordNotifier discordNotifier;

    /**
     * 새로운 WebSocket 세션을 Registry에 등록
     * 만약 같은 브라우저 탭 토큰을 가진 세션이 이미 존재할 경우,
     * 기존 세션 연결 종료 -> 새로운 세션으로 교체 (탭에서 가장 마지막에 연결된 세션만 활성화)
     *
     * @param session 등록할 새로운 WebSocket 세션
     */
    synchronized void register(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_KEY);
        String clientId = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.CLIENT_ID_KEY).toString();
        String tabWebSocketToken = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.WEBSOCKET_TOKEN_KEY).toString();

        WebSocketSession existingSession = browserTabSessions.get(tabWebSocketToken);
        if (existingSession != null) {
            log.warn("🟠 중복 WebSocket 연결 감지. userId={}, clientId={}, existingSession={}, newSession={}, tabWebSocketToken={}",
                    userId, clientId, existingSession.getId(), session.getId(), tabWebSocketToken);

            browserTabSessions.remove(tabWebSocketToken);
            if(!webSocketSessionCloser.tryCloseSession(existingSession, 4400, "Duplicate WebSocket connection")) {
                log.error("🟠 기존 세션 {} 종료 실패. (새로운 세션 등록은 계속 진행)", existingSession.getId());
            }

            String title = "🟠 중복 WebSocket 연결 감지";
            String description = String.format(
                    """
                    - userId=%s
                    - clientId=%s
                    - existingSession=%s 종료
                    - newSession=%s 관리 시작
                    - tabWebSocketToken=%s
                    """,
                    userId, clientId, existingSession.getId(), session.getId(), tabWebSocketToken
                    );
            discordNotifier.send(title, description, AlertLevel.CRITICAL);
        }

        browserTabSessions.put(tabWebSocketToken, session);
        clientTabTokens.computeIfAbsent(clientId, clientIdKey -> ConcurrentHashMap.newKeySet()).add(tabWebSocketToken);
        userClients.computeIfAbsent(userId, userIdKey -> ConcurrentHashMap.newKeySet()).add(clientId);
    }

    synchronized void unregisterSession(WebSocketSession session) {
        if (closingSessionTokens.contains(session.getId())) {
            closingSessionTokens.remove(session.getId());
            return;
        }

        Long userId = (Long) session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_KEY);
        String clientId = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.CLIENT_ID_KEY).toString();
        String tabWebSocketToken = session.getAttributes().get(WebSocketAuthHandshakeInterceptor.WEBSOCKET_TOKEN_KEY).toString();

        browserTabSessions.remove(tabWebSocketToken);

        clientTabTokens.computeIfPresent(clientId, (clientIdKey, tabTokens) -> {
            tabTokens.remove(tabWebSocketToken);

            if (tabTokens.isEmpty()) {
                userClients.computeIfPresent(userId, (userIdKey, clientIds) -> {
                    clientIds.remove(clientId);
                    return clientIds.isEmpty() ? null : clientIds;
                });
            }
            return tabTokens.isEmpty() ? null : tabTokens;
        });
    }

    synchronized void closeSessionsOnLogout(Long userId) {
        Set<String> clientIdsToRemove = userClients.remove(userId);
        if (clientIdsToRemove != null && !clientIdsToRemove.isEmpty()) {
            clientIdsToRemove
                    .forEach(clientId -> {
                        Set<String> tabTokensToRemove = clientTabTokens.remove(clientId);
                        if (tabTokensToRemove != null && !tabTokensToRemove.isEmpty()) {
                            tabTokensToRemove
                                    .forEach(browserTabToken -> {
                                        WebSocketSession session = browserTabSessions.remove(browserTabToken);
                                        if (session != null && session.isOpen()) {
                                            closingSessionTokens.add(session.getId());
                                            webSocketSessionCloser.tryCloseSession(session, CloseStatus.NORMAL);
                                        }
                                    });
                        }
                    });
        }
    }

    boolean hasSession(String tabWebSocketToken) {
        return browserTabSessions.containsKey(tabWebSocketToken);
    }
}
