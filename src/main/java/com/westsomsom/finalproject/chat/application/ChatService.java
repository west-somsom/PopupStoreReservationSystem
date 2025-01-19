package com.westsomsom.finalproject.chat.application;

import com.westsomsom.finalproject.chat.dao.ChatRepository;
import com.westsomsom.finalproject.chat.domain.Message;

import com.westsomsom.finalproject.store.dao.StoreRepository;
import com.westsomsom.finalproject.store.domain.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;
    private final StoreRepository storeRepository;

    public List<Message> getMessagesByStoreId(int storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found with id: " + storeId));
        return chatRepository.findByStore(store);
    }

    public void saveMessage(Message message) {
        chatRepository.save(message);
    }
}
