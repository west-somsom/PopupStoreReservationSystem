package com.westsomsom.finalproject.login.api;

import com.westsomsom.finalproject.login.application.KakaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@Controller
public class HomeController {
    private final KakaoService kakaoService;

    @GetMapping("/")
    public String login(Model model) {
        String kakaoUrl = kakaoService.getKakaoLogin();
        System.out.println("Kakao URL: " + kakaoUrl);  // 이 라인 추가

        //model.addAttribute("kakaoUrl", kakaoUrl);
        model.addAttribute("kakaoUrl", kakaoService.getKakaoLogin());

        return "index"; //스프링은 이 뷰 이름을 찾아서 해당 뷰를 렌더링 "index.html"
    }

    //추가된 내용
    @GetMapping("/info-form")
    public String showInfoForm() {
        return "info-form";
    }
}
