package com.westsomsom.finalproject.login.application;

import com.westsomsom.finalproject.login.dto.KakaoDto;
import com.westsomsom.finalproject.user.dao.UserInfoRepository;
import com.westsomsom.finalproject.user.domain.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class KakaoService {
    private final UserInfoRepository userInfoRepository;

    @Value("${kakao.client.id}")
    private String KAKAO_CLIENT_ID;

    @Value("${kakao.client.secret}")
    private String KAKAO_CLIENT_SECRET;

    @Value("${kakao.redirect.url}")
    private String KAKAO_REDIRECT_URL;

    private final static String KAKAO_AUTH_URI = "https://kauth.kakao.com";
    private final static String KAKAO_API_URI = "https://kapi.kakao.com";

    public String getKakaoLogin() {
        return KAKAO_AUTH_URI + "/oauth/authorize"
                + "?client_id=" + KAKAO_CLIENT_ID
                + "&redirect_uri=" + KAKAO_REDIRECT_URL
                + "&response_type=code";
    }

    public KakaoDto getKakaoInfo(String code) throws Exception {
        if (code == null) throw new Exception("Failed get authorization code");

        String accessToken = "";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-type", "application/x-www-form-urlencoded");

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", KAKAO_CLIENT_ID);
            params.add("client_secret", KAKAO_CLIENT_SECRET);
            params.add("code", code);
            params.add("redirect_uri", KAKAO_REDIRECT_URL);

            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    KAKAO_AUTH_URI + "/oauth/token",
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObj = (JSONObject) jsonParser.parse(response.getBody());

            accessToken = (String) jsonObj.get("access_token");

        } catch (Exception e) {
            throw new Exception("API call failed", e);
        }
        return getUserInfoWithToken(accessToken);
    }

    private KakaoDto getUserInfoWithToken(String accessToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        RestTemplate rt = new RestTemplate();
        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = rt.exchange(
                KAKAO_API_URI + "/v2/user/me",
                HttpMethod.POST,
                httpEntity,
                String.class
        );

        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObj = (JSONObject) jsonParser.parse(response.getBody());
        JSONObject account = (JSONObject) jsonObj.get("kakao_account");
        JSONObject profile = (JSONObject) account.get("profile");

        String userId = String.valueOf(jsonObj.get("id"));
        String email = String.valueOf(account.get("email"));
        String username = String.valueOf(profile.get("nickname"));

        UserInfo userInfo = new UserInfo();
        //userInfo.setUserId(userId); //Id값은 바뀌면 안됨
        userInfo.setEmail(email);
        userInfo.setUsername(username);
        userInfo.setPhone(""); // Default as empty
        userInfo.setGender(""); // Default as empty
        userInfo.setAge(0); // Default as 0
        userInfo.setCstAddrNo(""); // Default as empty

        userInfoRepository.save(userInfo);

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        session.setAttribute("userInfo", userInfo);

        return KakaoDto.builder()
                .id(Long.parseLong(userId))
                .email(email)
                .nickname(username).build();
    }
}
