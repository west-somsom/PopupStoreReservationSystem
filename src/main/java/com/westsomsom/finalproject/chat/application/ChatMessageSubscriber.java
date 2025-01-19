package com.westsomsom.finalproject.chat.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westsomsom.finalproject.chat.dao.ChatRepository;
import com.westsomsom.finalproject.chat.domain.Message;
import com.westsomsom.finalproject.chat.dto.ChatMessageDto;
import com.westsomsom.finalproject.store.dao.StoreRepository;
import com.westsomsom.finalproject.store.domain.Store;
import com.westsomsom.finalproject.user.dao.UserInfoRepository;
import com.westsomsom.finalproject.user.domain.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageSubscriber implements MessageListener {
    private final ObjectMapper mapper;
    private final ChatRepository chatRepository;
    private final UserInfoRepository userInfoRepository;
    private final StoreRepository storeRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        String channel = new String(pattern);
        String receivedMessage = new String(message.getBody());

        log.info("Received message: {} from channel: {}", receivedMessage, channel);

        processMessage(receivedMessage);
    }

    private void processMessage(String receivedMessage) {
        try {
            log.debug("Raw received message: {}", receivedMessage);

            JsonNode rootNode = mapper.readTree(receivedMessage);

            // JSON이 이중으로 감싸져 있는지 확인
            if (rootNode.isTextual()) {
                log.debug("JSON이 String으로 감싸져 있음, 다시 파싱 수행");
                rootNode = mapper.readTree(rootNode.asText());
            }

            log.debug("파싱된 JSON 트리: {}", rootNode);

            // 필드 존재 여부 검증 후 처리
            if (!rootNode.has("messageType") || !rootNode.has("storeId") ||
                    !rootNode.has("sender") || !rootNode.has("message")) {
                log.error("필수 필드가 존재하지 않음: {}", rootNode);
                return;
            }

            // JSON 필드 추출
            String messageType = rootNode.get("messageType").asText();
            int storeId = rootNode.get("storeId").asInt();
            String sender = rootNode.get("sender").asText();
            String message = rootNode.get("message").asText();

            log.debug("messageType: {}, storeId: {}, sender: {}, message: {}",
                    messageType, storeId, sender, message);

            // ChatMessageDto로 변환
            ChatMessageDto chatMessageDto = mapper.treeToValue(rootNode, ChatMessageDto.class);
            log.debug("역직렬화된 ChatMessageDto: {}", chatMessageDto);

            // Redis에 메시지 저장
            saveMessageToRedis(chatMessageDto);

            log.info("Redis Subscriber 메시지 처리 완료: {}", chatMessageDto);
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            log.error("JSON 역직렬화 실패: {}", receivedMessage, e);
        } catch (Exception e) {
            log.error("Redis Subscriber 메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    private void saveMessageToRedis(ChatMessageDto chatMessageDto) {
        try {
            // Redis에 저장할 키 생성: "Chat|<storeId>|<userId>|content"
            String redisKey = "Chat|" + chatMessageDto.getStoreId() + "|" + chatMessageDto.getSender() + "|"
                    + chatMessageDto.getMessage();

            // 메시지를 Redis에 저장
            redisTemplate.opsForValue().set(redisKey, chatMessageDto.getMessage());

            log.info("Redis에 채팅 메시지가 저장됨: key = {}", redisKey);
        } catch (Exception e) {
            log.error("Redis에 채팅 메시지를 저장하는 데 오류 발생: {}", e.getMessage(), e);
        }
    }

    private void saveMessageToDB(ChatMessageDto chatMessageDto) {
        try {
            // senderId를 String으로 처리
            String senderId = chatMessageDto.getSender();

            UserInfo userInfo = userInfoRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + senderId));

            Store store = storeRepository.findById(chatMessageDto.getStoreId())
                    .orElseThrow(() -> new RuntimeException("Store not found with id: " + chatMessageDto.getStoreId()));

            // Message 객체 생성
            Message message = Message.builder()
                    .userInfo(userInfo)
                    .content(chatMessageDto.getMessage())
                    .store(store)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            // DB 저장
            chatRepository.save(message);

            log.info("Message saved to DB: {}", message);
        } catch (Exception e) {
            log.error("Error saving message to DB: {}", e.getMessage(), e);
        }
    }
}
