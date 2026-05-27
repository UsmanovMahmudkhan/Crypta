package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.DeliveryReceiptRequest;
import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageResponse;
import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageSubmitRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.MessageInboxRequest;
import com.sovereigncomm.service.DirectMessageService;
import com.sovereigncomm.service.GroupMessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/messages")
public class MessageController {
    private final DirectMessageService directMessageService;
    private final GroupMessageService groupMessageService;

    public MessageController(DirectMessageService directMessageService, GroupMessageService groupMessageService) {
        this.directMessageService = directMessageService;
        this.groupMessageService = groupMessageService;
    }

    @PostMapping("/direct")
    IdResponse submitDirect(@Valid @RequestBody EncryptedMessageSubmitRequest request) {
        return directMessageService.submit(request);
    }

    @PostMapping("/groups")
    IdResponse submitGroup(@Valid @RequestBody EncryptedMessageSubmitRequest request) {
        return groupMessageService.submit(request);
    }

    @GetMapping("/inbox")
    List<EncryptedMessageResponse> inbox(
            @RequestParam UUID deviceId,
            @RequestParam(required = false) Instant after,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        return directMessageService.inbox(new MessageInboxRequest(deviceId, after, limit));
    }

    @PostMapping("/receipts")
    void receipt(@Valid @RequestBody DeliveryReceiptRequest request) {
        directMessageService.recordReceipt(request);
    }
}
