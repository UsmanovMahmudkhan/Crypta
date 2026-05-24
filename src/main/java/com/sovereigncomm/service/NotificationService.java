package com.sovereigncomm.service;

import java.util.UUID;

public interface NotificationService {
    void sendGenericNotification(UUID deviceId, String urgency);
}
