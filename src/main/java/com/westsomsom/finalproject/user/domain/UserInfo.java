package com.westsomsom.finalproject.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "UserInfo")
public class UserInfo {
    @Id
    private String userId;

    @Column(nullable = false, length = 30)
    private String email;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 13)
    private String phone;

    @Column(nullable = false, length = 1)
    private String gender;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false, length = 30)
    private String cstAddrNo;
}
