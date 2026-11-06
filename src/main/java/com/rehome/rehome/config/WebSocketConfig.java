package com.rehome.rehome.config;

import com.rehome.rehome.security.WebSocketAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Chat (F-089/F-090/F-091) và notification (F-092/F-093/F-094) real-time qua STOMP over WebSocket.
 *
 * <p>Endpoint handshake: {@code /ws} (STOMP thuần qua WebSocket, cho app di động) và
 * {@code /ws-sockjs} (có SockJS fallback, cho web browser không hỗ trợ WebSocket thẳng). Client
 * kết nối xong gửi JWT trong header STOMP "Authorization" của frame CONNECT - không phải header
 * HTTP của request handshake, vì browser không tự set được header tùy ý khi mở WebSocket/SockJS -
 * xem {@link WebSocketAuthChannelInterceptor}.
 *
 * <p>Quy ước địa chỉ:
 * <ul>
 *   <li>{@code /topic/conversations.{conversationId}} - tin nhắn mới của 1 hội thoại. Chỉ subscribe
 *       được nếu đang là participant của hội thoại đó (kiểm ở {@link WebSocketAuthChannelInterceptor}
 *       qua {@link com.rehome.rehome.service.ConversationService#isParticipant}) - cùng điều kiện
 *       REST {@code GET /conversations/{id}} đang dùng.</li>
 *   <li>{@code /user/queue/notifications} - thông báo (F-092/F-093/F-094) của riêng người đang kết nối;
 *       Spring tự định tuyến theo Principal nên không cần kiểm quyền thêm.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP thuần qua WebSocket - app di động dùng thư viện stomp client kết nối trực tiếp
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        // Có SockJS fallback - web browser không mở được WebSocket trực tiếp (proxy/firewall chặn)
        registry.addEndpoint("/ws-sockjs").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
    }
}
