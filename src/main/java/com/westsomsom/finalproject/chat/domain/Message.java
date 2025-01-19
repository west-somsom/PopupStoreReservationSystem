package com.westsomsom.finalproject.chat.domain;

import com.westsomsom.finalproject.store.domain.Store;
import com.westsomsom.finalproject.user.domain.UserInfo;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "Message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int messageId;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private UserInfo userInfo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "storeId", nullable = false)
    private Store store;

    @Column(nullable = false)
    private LocalDateTime timestamp;  // 메시지 전송 시간

    @Builder
    public Message(UserInfo userInfo, String content, Store store, LocalDateTime timestamp) {
        this.userInfo = userInfo;
        this.content = content;
        this.store = store;
        this.timestamp = timestamp;
    }
}
