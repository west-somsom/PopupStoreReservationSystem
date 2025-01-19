package com.westsomsom.finalproject.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westsomsom.finalproject.chat.application.ChatMessagePublisher;
import com.westsomsom.finalproject.chat.application.ChatMessageSubscriber;
import com.westsomsom.finalproject.chat.application.ChatService;
import com.westsomsom.finalproject.chat.dao.ChatRepository;
import com.westsomsom.finalproject.chat.domain.Message;
import com.westsomsom.finalproject.chat.dto.ChatMessageDto;
import com.westsomsom.finalproject.store.application.StoreService;
import com.westsomsom.finalproject.store.dao.StoreRepository;
import com.westsomsom.finalproject.store.domain.Store;
import com.westsomsom.finalproject.user.dao.UserInfoRepository;
import com.westsomsom.finalproject.user.domain.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class WebSocketChatHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final StoreService storeService;
    private final ChatRepository chatRepository;
    private final UserInfoRepository userInfoRepository;
    private final StoreRepository storeRepository;
    private final ChatMessagePublisher chatMessagePublisher;
    private final ChatMessageSubscriber chatMessageSubscriber;

    // 팝업 스토어 ID별 WebSocket 세션 관리 Map
    private final Map<Integer, Set<WebSocketSession>> storeSessions = new HashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("새로운 WebSocket 연결: {}", session.getId());

        int storeId = extractStoreIdFromSession(session);

        // Store 존재 여부 확인
        storeService.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found with id: " + storeId));

        // 기존 메시지 클라이언트로 전송
        List<Message> messages = chatService.getMessagesByStoreId(storeId);
        for (Message message : messages) {
            String jsonMessage = objectMapper.writeValueAsString(convertMessageToDto(message));
            session.sendMessage(new TextMessage(jsonMessage));
        }

        // 세션 추가
        storeSessions.computeIfAbsent(storeId, k -> new HashSet<>()).add(session);
        session.sendMessage(new TextMessage("WebSocket 연결 완료 및 기존 메시지 불러오기 완료"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        log.info("메시지 수신: {}", textMessage.getPayload());

        try {
            ChatMessageDto chatMessageDto = objectMapper.readValue(textMessage.getPayload(), ChatMessageDto.class);
            log.info("ChatMessageDto: {}", chatMessageDto);

            int popupStoreId = chatMessageDto.getStoreId();

            if (chatMessageDto.getMessageType().equals(ChatMessageDto.MessageType.JOIN)) {
                storeSessions.computeIfAbsent(popupStoreId, s -> new HashSet<>()).add(session);
                chatMessageDto.setMessage(chatMessageDto.getSender() + "님이 입장하셨습니다.");
            } else if (chatMessageDto.getMessageType().equals(ChatMessageDto.MessageType.LEAVE)) {
                storeSessions.getOrDefault(popupStoreId, new HashSet<>()).remove(session);
                chatMessageDto.setMessage(chatMessageDto.getSender() + "님이 퇴장하셨습니다.");
            } else if (chatMessageDto.getMessageType().equals(ChatMessageDto.MessageType.TALK)) {
                // 채팅 메시지 DB에 저장
                saveMessageToDB(chatMessageDto);
            }

            // ChatMessagePublisher를 통해 메시지를 Redis로 발행
            chatMessagePublisher.publish(textMessage.getPayload());

            // 동일 Store의 모든 세션에 메시지 브로드캐스트
            int storeId = chatMessageDto.getStoreId();
            Set<WebSocketSession> sessions = storeSessions.getOrDefault(storeId, Collections.emptySet());
            for (WebSocketSession wsSession : sessions) {
                if (wsSession.isOpen()) {
                    wsSession.sendMessage(textMessage);
                }
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            session.sendMessage(new TextMessage("Error: " + e.getMessage()));
        }
    }

    private void saveMessageToDB(ChatMessageDto chatMessageDto) {
        // 실제 사용자 정보를 DB에서 조회해야 합니다
        UserInfo userInfo = userInfoRepository.findById(chatMessageDto.getSender())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 실제 스토어 정보를 DB에서 조회해야 합니다
        Store store = storeRepository.findById(chatMessageDto.getStoreId())
                .orElseThrow(() -> new RuntimeException("Store not found"));

        Message message = new Message();
        message.setUserInfo(userInfo);
        message.setStore(store);
        message.setContent(chatMessageDto.getMessage());
        message.setTimestamp(java.time.LocalDateTime.now());

        chatRepository.save(message); // DB에 저장
        log.info("메시지 저장 완료: {}", message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket 연결 종료: {}", session.getId());
        storeSessions.values().forEach(sessions -> sessions.remove(session));
    }

    private int extractStoreIdFromSession(WebSocketSession session) {
        String query = Objects.requireNonNull(session.getUri()).getQuery();
        return Integer.parseInt(Arrays.stream(query.split("&"))
                .filter(param -> param.startsWith("storeId="))
                .findFirst()
                .orElse("storeId=0")
                .split("=")[1]);
    }

    private ChatMessageDto convertMessageToDto(Message message) {
        return ChatMessageDto.builder()
                .messageType(ChatMessageDto.MessageType.TALK)
                .storeId(message.getStore().getStoreId())  // Store에서 getStoreId() 호출
                .sender(String.valueOf(message.getUserInfo().getUserId()))  // UserInfo에서 getUserId() 호출
                .message(message.getContent())
                .build();
    }
}
