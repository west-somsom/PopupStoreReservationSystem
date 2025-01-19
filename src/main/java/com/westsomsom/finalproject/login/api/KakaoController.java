package com.westsomsom.finalproject.login.api;

import com.westsomsom.finalproject.login.response.MsgEntity;
import com.westsomsom.finalproject.login.application.KakaoService;
import com.westsomsom.finalproject.login.dto.KakaoDto;
import com.westsomsom.finalproject.user.dao.UserInfoRepository;
import com.westsomsom.finalproject.user.domain.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("kakao")
public class KakaoController {
    private final KakaoService kakaoService;
    private final UserInfoRepository userInfoRepository;

    @GetMapping("/callback")
    public ResponseEntity<MsgEntity> callback(HttpServletRequest request) throws Exception {
        KakaoDto kakaoInfo = kakaoService.getKakaoInfo(request.getParameter("code"));

        return ResponseEntity.ok()
                .body(new MsgEntity("Success", kakaoInfo));
    }

    @PostMapping("/update-info")
    public ResponseEntity<MsgEntity> updateUserInfo(
            @RequestParam("phone") String phone,
            @RequestParam("gender") String gender,
            @RequestParam("age") int age,
            @RequestParam("cstAddrNo") String cstAddrNo,
            HttpSession session) {

        UserInfo userInfo = (UserInfo) session.getAttribute("userInfo");
        if (userInfo == null) {
            return ResponseEntity.badRequest().body(new MsgEntity("No logged-in user found", null));
        }

        UserInfo sessionUserInfo = userInfoRepository.findByUserId(userInfo.getUserId());
        if (sessionUserInfo == null) {
            throw new IllegalStateException("User not found in database");
        }
        sessionUserInfo.setPhone(phone);
        sessionUserInfo.setGender(gender);
        sessionUserInfo.setAge(age);
        sessionUserInfo.setCstAddrNo(cstAddrNo);

        userInfoRepository.save(sessionUserInfo);

        session.setAttribute("userInfo", sessionUserInfo);

        return ResponseEntity.ok(new MsgEntity("User info updated successfully", sessionUserInfo));
    }
}
