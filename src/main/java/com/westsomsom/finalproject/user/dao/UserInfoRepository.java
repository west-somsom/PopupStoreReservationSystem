package com.westsomsom.finalproject.user.dao;

import com.westsomsom.finalproject.user.domain.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    UserInfo findByUserId(String userId);
}
