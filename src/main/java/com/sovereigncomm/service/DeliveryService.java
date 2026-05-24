package com.sovereigncomm.service;

import java.util.UUID;

public interface DeliveryService {
    void enqueueForRecipient(UUID recipientDeviceId, String encryptedEnvelope);
}
