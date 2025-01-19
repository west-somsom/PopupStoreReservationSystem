package com.westsomsom.finalproject.chat.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageDto {
    // 메시지  타입 : 입장, 채팅, 퇴장
    public enum MessageType{
        JOIN, TALK, LEAVE;

        @JsonCreator
        public static MessageType fromString(String key) {
            return MessageType.valueOf(key.toUpperCase());
        }
    }

    private MessageType messageType;
    private int storeId;
    private String sender;
    private String message;
}
