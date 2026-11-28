package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.PushNotificationService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "false", matchIfMissing = true)
public class NoopPushNotificationServiceImpl implements PushNotificationService {

    @Override
    public void send(Account recipient, String title, String body, Map<String, String> data) {
    }
}
