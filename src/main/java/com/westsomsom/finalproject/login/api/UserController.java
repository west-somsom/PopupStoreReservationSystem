package com.westsomsom.finalproject.login.api;

import com.westsomsom.finalproject.login.response.MsgEntity;
import com.westsomsom.finalproject.user.dao.UserInfoRepository;
import com.westsomsom.finalproject.user.domain.UserInfo;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class UserController {
    private final UserInfoRepository userInfoRepository;

    @GetMapping("/api/user/{userId}")
    public ResponseEntity<MsgEntity> getUserInfo(@PathVariable("userId") String userId) {
        UserInfo userInfo = userInfoRepository.findByUserId(userId);

        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MsgEntity("User not found", null));
        }

        return ResponseEntity.ok(new MsgEntity("User information fetched successfully", userInfo));
    }

    @PutMapping("/api/user/{userId}")
    public ResponseEntity<MsgEntity> updateUserInfo(
            @PathVariable("userId") String userId,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "age", required = false) Integer age,
            @RequestParam(value = "cstAddrNo", required = false) String cstAddrNo) {

        UserInfo userInfo = userInfoRepository.findByUserId(userId);
        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MsgEntity("User not found", null));
        }

        // userId 수정 방지
        if (!userInfo.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MsgEntity("UserId cannot be changed", null));
        }

        // 필요한 값만 업데이트
        if (phone != null) {
            userInfo.setPhone(phone);
        }
        if (gender != null) {
            userInfo.setGender(gender);
        }
        if (age != null) {
            userInfo.setAge(age);
        }
        if (cstAddrNo != null) {
            userInfo.setCstAddrNo(cstAddrNo);
        }
        userInfoRepository.save(userInfo);

        return ResponseEntity.ok(new MsgEntity("User information updated successfully", userInfo));
    }
}
