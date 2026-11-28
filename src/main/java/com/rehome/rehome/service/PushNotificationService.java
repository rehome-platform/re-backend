package com.rehome.rehome.service;

import com.rehome.rehome.entity.auth.Account;
import java.util.Map;

public interface PushNotificationService {

    void send(Account recipient, String title, String body, Map<String, String> data);
}
