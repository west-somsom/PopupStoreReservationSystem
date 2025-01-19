package com.westsomsom.finalproject.chat.api;

import org.springframework.web.bind.annotation.RestController;
import com.westsomsom.finalproject.chat.domain.Message;
import com.westsomsom.finalproject.chat.application.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @GetMapping("/{storeId}")
    public ResponseEntity<List<Message>> getMessagesByStore(@PathVariable int storeId) {
        List<Message> messages = chatService.getMessagesByStoreId(storeId);
        return ResponseEntity.ok(messages);
    }
}
