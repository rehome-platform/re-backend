package com.rehome.rehome.security;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.service.ConversationService;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Chạy trên inbound channel của STOMP (mọi frame client gửi lên), xử lý 2 việc JwtAuthenticationFilter
 * làm cho REST nhưng WebSocket cần làm riêng vì không đi qua Spring Security filter chain theo
 * từng request HTTP - 1 kết nối WebSocket sống xuyên suốt nhiều frame STOMP khác nhau:
 *
 * <ol>
 *   <li>CONNECT - xác thực JWT lấy từ STOMP header "Authorization" (client không set được header
 *       HTTP tùy ý lúc mở WebSocket/SockJS nên JWT phải nằm trong frame STOMP, không phải header
 *       handshake). Set {@code accessor.setUser(...)} nếu hợp lệ - Principal này được dùng suốt đời
 *       session để Spring định tuyến {@code /user/queue/**} và để nơi khác đọc quyền.</li>
 *   <li>SUBSCRIBE - với các topic theo hội thoại ({@code /topic/conversations.{id}}), chặn nếu
 *       người kết nối không phải participant của hội thoại đó, cùng điều kiện với
 *       {@code ConversationServiceImpl.requireParticipant} dùng cho REST.</li>
 * </ol>
 *
 * <p>Throw exception ở đây làm Spring gửi 1 frame ERROR về client và đóng kết nối STOMP - đúng ý
 * định (không có CONNECTED/token sai, hoặc không có quyền subscribe thì không cho nhận tin tiếp).
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CONVERSATION_TOPIC_PATTERN =
            Pattern.compile("^/topic/conversations\\.([0-9a-fA-F-]{36})$");

    private final JwtService jwtService;
    private final AccountDetailsServiceImpl accountDetailsService;
    // ObjectProvider, không inject ConversationService trực tiếp: ConversationServiceImpl cần
    // SimpMessagingTemplate (được tạo bởi DelegatingWebSocketMessageBrokerConfiguration, chính là
    // thứ cấu hình từ WebSocketConfig) nên inject thẳng tạo vòng phụ thuộc lúc khởi động
    // (WebSocketConfig -> interceptor -> ConversationService -> SimpMessagingTemplate -> WebSocketConfig).
    // Trì hoãn resolve tới lúc dùng thật (mỗi lần SUBSCRIBE) để cắt vòng này.
    private final ObjectProvider<ConversationService> conversationServiceProvider;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Thiếu Authorization header trong frame CONNECT.");
        }
        String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            String email = jwtService.extractEmail(jwt);
            Account account = (Account) accountDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(jwt, account)) {
                throw new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn.");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccessDeniedException("Tài khoản đã bị khoá.");
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(account, null, account.getAuthorities());
            accessor.setUser(authToken);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            throw new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn.");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = CONVERSATION_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            // Địa chỉ khác (vd. /user/queue/notifications) không cần kiểm thêm ở đây
            return;
        }

        Account viewer = currentAccount(accessor);
        UUID conversationId = UUID.fromString(matcher.group(1));
        if (!conversationServiceProvider.getObject().isParticipant(viewer, conversationId)) {
            throw new AccessDeniedException("Bạn không thuộc hội thoại này.");
        }
    }

    private Account currentAccount(StompHeaderAccessor accessor) {
        Object user = accessor.getUser();
        if (!(user instanceof UsernamePasswordAuthenticationToken token)
                || !(token.getPrincipal() instanceof Account account)) {
            throw new BadCredentialsException("Chưa xác thực qua CONNECT.");
        }
        return account;
    }
}
