[DDWU ACC 2nd] Popup Store Reservation System
=
## **프로젝트 소개**
팝업스토어 예약 및 추천 서비스로, 팝업스토어 예약 오픈 시점에 트래픽이 몰리며 이를 대응할 수 있는 서비스이다. 사용자는 자신과 비슷한 조건을 가진 그룹의 카드 결제 내역을 토대로 기반으로 다른 팝업스토어를 추천받을 수 있다.

https://github.com/west-somsom/PopupStoreReservationSystem

### **주요 기능**
1. 사용자 관리
   - 카카오 소셜 로그인을 통한 간편 로그인
   - 사용자 정보 등록, 조회 및 수정
2. 팝업 스토어 정보 관리
   - 팝업 스토어 등록
   - 팝업 스토어 정보 전체 목록 조회 및 상세 조회
   - 이름 및 카테고리 기반 검색
3. 채팅을 통한 소통 지원
   - 예약 등록 시점에 채팅방 함께 개설
   - 팝업 스토어 당 한 개의 1:N 채팅방
   - 텍스트 채팅
4. 예약 관리
   - 예약 신청
   - 예약 중 사용자의 현재 순번 실시간 확인
   - 예약 내역 조회
   - 예약 취소 및 삭제
5. 알림 전송
   - 예약 즉시 예약 확인 이메일 알림 전송
   - 예약일 하루 전(오후 3시) 예약 확인 및 알림 전송
6. 아이템 기반 사용자 맞춤형 추천
   - 사용자 프로필 및 카드 결제 내역 데이터를 기반으로 개인화된 팝업 스토어 추천 제공

## **아키텍처 설계**
- 팝업 스토어 예약 서비스는 예약 오픈 시점에 대량의 사용자 접속 발생
  - 특정 기간 동안 사용자 트래픽 폭증 -> 트래픽 부하 분산 및 관리 필요
- 사용자 데이터의 무결성과 서비스 안정성을 보장하기 위해 Multi-AZ 기반의 이중화 구성이 필요

### **아키텍처 설계 및 구성**
### **Infra**
![아키텍처 구조도 drawio (1)](https://github.com/user-attachments/assets/469f1b35-513a-4ff2-9d77-c5fb6c770cab)
- Public과 Private으로 분리하여 외부 사용자가 접근하는 네트워크와 데이터가 저장된 네트워크 분리
  - Public Subnet
    - 외부 트래픽 처리
    - Private Subnet에 위치한 인스턴스 접근을 위해 Bastion host 설정
    - Private Subnet에 위치한 인스턴스의 인터넷 사용을 위해 NAT Instance 설정
  - Private Subnet
    - 데이터 베이스와 캐시, 애플리케이션 서버 등 민감한 데이터가 저장된 자원을 배치
    - Bastion Host를 통해 간접적으로 접근 가능
- 트래픽 분산
   - 서비스의 안정성을 위해 2개의 가용 영역 사용
   - ALB를 통한 인스턴스 부하 분산
   - AutoScaling 설정 및 ALB 대상 그룹 지정
- 모니터링
   - CloudWatch를 통한 로그 모니터링
- 팝업 스토어 정보 등록 및 조회 기능 구현
  1. Redis OSS with ElastiCache 활용
     - 등록 기능
       - 팝업 스토어 정보를 DB와 Redis에 동시에 저장하여 조회 속도 최적화
       - 등록 시, Redis 캐시에 중복 데이터가 저장되지 않도록 유효성 검사를 추가로 수행
     - 조회 기능
       - 전체 정보 조회 시 Redis에서 우선 데이터 조회
       - 캐시 미스 발생 시 DB에서 데이터를 조회하고, 이를 Redis에 저장
  2. 현재 채택 방식
     - 조회 기능은 **RDS 직접 조회 방식**을 사용 중으로, 캐시를 활용한 방식은 향후 효율성을 검토해 개선 예정
       
- 예약 알림 기능 구현
  - **EventBridge Scheduler → SQS → Lambda → SES 트리거 연결 구조**
    - Spring Boot 서버에서 EventBridge Scheduler를 생성하기 위한 SDK를 호출
    - 생성된 Scheduler가 SQS로 메시지를 전송
    - SQS 메시지가 Lambda Trigger를 통해 Lambda 실행
    - Lambda에서 SES 이메일 발송을 위한 SDK를 호출
  - **Scheduler 생성 시 전달한 파라미터의 내용이 SES를 통해 이메일로 전송**
    
- 예약 기능 구현
  - **Redis OSS with ElastiCache 사용**
    - **예약 가능 인원수 제한을 위해 슬롯 초기화(10개)**
    - **사용자 예약 요청 처리**
      - Redis의 Set을 사용해 사용자가 이미 대기열에 있는지 또는 예약 요청을 보낸 적이 있는지 확인
      - 확인 후 Redis 대기열(List)에 추가
    - **Redis Pub/Sub(Publish/Subscribe)를 통해 실시간으로 예약 처리**
      - 초반에는 스케줄러를 사용하였으나 이후 성능 개선을 위해 Redis Pub/Sub 도입(스파이크 테스트)
      - Redis Pub/Sub 방식
        - Redis에서 제공하는 실시간 이벤트 기반 비동기 메시징 시스템
        - 메시지를 발행하고 이를 구독하는 애플리케이션 간 실시간 메시지 전달
        - 예약 요청이 들어오면 메시지를 Redis 채널에 발행
              `redisTemplate.convertAndSend(channel, message);`
        - Redis는 해당 채널 구독 중인 구독자에게 메시지 전달
        - 메시지를 수신한 후 예약 요청 처리
       - 예약 가능 슬롯키 확인 후 순서대로 예약 처리
       - 중복 예약을 막기 위해 예약한 사용자를 Redis에 추가
    - **TTL 만료 설정을 팝업 스토어 예약 기간 동안 유지**
    - **예약 취소 시 Redis에서 슬롯 수 업데이트 및 사용자 Redis 중복 확인 데이터에서 제거**          

## **Back-End**
### **소셜 로그인 서비스**
- 카카오 오픈 API를 활용한 소셜 로그인/로그아웃 구현
- 흐름
  - **로그인 흐름**: 카카오 로그인 요청 → 콜백으로 인증 코드 수신 → 액세스 토큰 발급 → 사용자 정보 조회 및 저장
  - **정보 업데이트**: API를 통해 사용자 정보를 수정 및 유지
1. 카카오 인증 및 사용자 정보 관리
    - 카카오 로그인 URL 생성 : https://kauth.kakao.com/oauth/authorize를 활용해 로그인 요청 URL 생성
    - 액세스 토큰 발급 : 사용자 인증 후 받은 code로 카카오 API에서 토큰 요청
    - 사용자 정보 조회 : 액세스 토큰으로 사용자 정보 조회 후 DB에서 저장 및 세션 생성
2. 콜백 및 사용자 정보 갱신
    - 카카오 로그인 콜백: 토큰 및 사용자 정보 처리 후 성공 메시지 반환
    - 사용자 정보 업데이트: 전화번호, 성별, 나이 등 사용자 정보를 업데이트(추가적으로 입력받음)하고 DB 및 세션 동기화
3. 사용자 정보 API
    - 사용자 정보 조회: 특정 사용자의 정보를 DB에서 검색 및 반환
    - 사용자 정보 수정: 입력된 정보만 선택적으로 업데이트
      
### **추천 서비스**
- **구매 데이터 기반 팝업 스토어 추천 시스템**
- **접근 방식**:
  - **협업 필터링**(collaborative filtering)과 **아이템 기반** **코사인 유사도** (cosine similarity)활용
  - Precision@K, Recall@K, F1-Score 등의 평가 지표를 사용하여 성능 검증
    
||협업 필터링|코사인 유사|
|------|---|---|
|**목적**|사용자 및 아이템 간 유사성을 바탕으로 추천|두 품목의 구매 패턴 방향성을 기반으로 유사성 측정|
|**장점**|데이터 패턴 분석을 통한 직관적 추천|구매 금액의 크기보다 패턴에 집중, 희소 행렬에 적합|
|**사용 이유**|대규모 사용자와 다양한 아이템 조합을 다룰 수 있음|효율적 계산과 사용자 구매 패턴 유사성 확인|

- 데이터 개요:
<img width="594" alt="image" src="https://github.com/user-attachments/assets/cd125cd4-8fa4-4816-9329-1d0fd4fb9050" />

   - **데이터 크기**: 118,176개의 구매 기록과 10개의 특성 (성별, 나이, 구매금액 등)
   - **주요 특징**: 사용자 ID는 주소, 성별, 나이를 결합해 생성되며, 구매 금액을 추천 점수 계산에 사용
      
- 사용자 ID를 입력받아, 유사도 기반 상위 N개 추천 품목을 출력
- **시각화 및 통찰**
  - **성별 분석** : 여성 사용자가 남성보다 높은 구매 금액을 기록
  - **연령대별 구매 경향**: 30대 사용자층이 가장 높은 구매력을 보임
  - **시간대 분석**: 주말 구매가 주중보다 많으며, 오후 구매가 오전보다 높음
  - **품목별 구매 경향**: 특정 품목 카테고리의 구매 금액 비중이 높음
  - **월별 매출 추이**: 계절별 매출 변화 확인
  - **고빈도 고객 분석**: 상위 5명의 고빈도 구매 고객 식별-> 활용 방안 : 해당 고객을 타겟으로한 로열티 프로그램 기획 가능
- 성능 평가 결과
  - **Precision@K**: 추천 품목 중 실제 구매한 품목 비율
  - **Recall@K**: 실제 구매 품목 중 추천된 품목 비율
  - **F1-Score**: Precision과 Recall의 조화 평균
  - **결과**: 우수한 Precision@3(0.8020)를 보임
- 팝업 시스템 적용
  - 사용자가 회원가입을 하면, 개인정보 내역에서 지역, 성별, 나이를 불러와 input 함수에
입력이 되고 user_id에 맞는 추천 결과(품목 코드)를 반환해 **그에 맞는 팝업 스토어를 화
면에 띄어주어 클릭을 유도**

### **예약 서비스**
- List와 Set을 결합하여 Redis 대기열 사용
- Redis Pub/Sub을 사용하여 예약 처리 자동화
- 날짜와 시간대 별로 정해진 인원수만 선착순으로 처리
- Redis에서 예약 가능한 인원수 확인
- 대기열에서 순서대로 예약을 처리하고 슬롯 데이터 업데이트
- Redis 키에 TTL을 설정하여 예약 가능 기간 종료 후 데이터 자동 삭제
- 예약 시 중복 여부를 확인해 무분별한 요청 방지


**[어플리케이션 구성(500명이 요청하는 경우)]**

(1) 100명의 사용자가 특정 날짜의 특정 시간대에 예약 요청

(2) 100명의 사용자는 요청 순서대로 대기열에 등록  

   중복으로 예약 요청을 넣을 수 없도록 Redis에 등록
   
(3) 스케줄러가 동작하여 대기열에 있는 사용자 예약 처리

(4) 성공 시 선착순으로 10명씩 예약 성공

(5) 실패 시 “예약이 마감되었습니다.”라는 응답 반환

(6) 대기 시간 동안 현재 자신의 순번과 뒤에 남은 사람 수 확인 가능

(7) 위의 과정을 통해 이벤트 종료(특정 시간대에 10명 예약 성공) 
  
### **채팅 서비스**
- **WebSocket을 활용한 실시간 채팅 기능**
    - WebSocket을 사용하여 클라이언트와 서버 간 실시간 양방향 통신 구현
    - `Spring WebSocket` 을 통해 채팅 메시지의 입장, 퇴장, 채팅 내용을 실시간으로 주고받을 수 있도록 처리
- WebSocket 연결 및 메시지 전송 및 저장 흐름
    - 클라이언트가 WebSocket 연결을 요청하면, `WebSocketHandler` 에서 연결을 수립하고, 연결 성공 메시지를 클라이언트에 전송
    - 클라이언트에서 채팅 메시지를 전송하면, 이를 `ChatMessageDto`  객체로 변환하여 처리하고, 메시지 유형(입장, 채팅, 퇴장)에 맞게 적절한 처리를 수행
    - **입장 (JOIN)** 메시지는 해당 팝업 스토어에 참여한 세션을 관리하는 `popupStoreSessions` 맵에 세션을 추가
    - **퇴장 (LEAVE)** 메시지는 해당 팝업 스토어의 세션에서 연결을 종료
    - **채팅 (TALK)** 메시지는 Message 객체로 변환하여 DB에 저장하고, 모든 연결된 클라이언트에 실시간으로 메시지를 전송
- 채팅 메시지 조회
    - 해당 스토어에 속한 모든 메시지를 조회하여 반환

### **검색 서비스**
- QueryDSL을 사용해 팝업스토어 검색 기능 구현
  - 이름, 팝업스토어 시작일, 마감일, 위치 값을 쿼리스트링으로 입력해 검색
  - 카테고리 값을 쿼리스트링으로 입력해 검색
      
## **기능 시연**
**시연 영상**
https://drive.google.com/file/d/1np6pBRT7CSKuDsdFETkB0GAE_jj3KigE/view
  
## **성능 테스트**
### **1. 스모크 테스트**
**목적**: 주요 기능이 정상 작동하는지 확인하는 간단한 테스트.
1. **가상 사용자 수**: 50명
    - 최소한의 사용자 수로 주요 기능을 빠르게 확인.
2. **지속시간**: 3분
    - 시스템이 정상 동작을 보장하는지 빠르게 검증.
3. **피크타임, 램프업, 다운 여부 및 조건**:
    - 램프업: 1분 동안 0명 → 50명
    - 피크타임: 1분
    - 다운: 1분 동안 50명 → 0명
4. **사용 시나리오**:
    - **팝업 스토어 정보 전체 조회**: 데이터를 정상적으로 불러오는지 확인.
    - **예약 신청**: 예약 데이터가 정상적으로 들어가는지 확인.
    - **현재 순번 확인:** 예약 관련 데이터가 올바르게 표시되는지 검증.

코드
```javascript
import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend } from 'k6/metrics';

// Custom metrics
const Response_Time = new Trend('Response_Time');

export const options = {
  stages: [
    { duration: '1m', target: 50 }, 
    { duration: '1m', target: 50 },
    { duration: '1m', target: 0 },
  ],
};

export default function () {
  const baseUrl = 'http://west-somsom-alb-1941268033.ap-northeast-2.elb.amazonaws.com';

  // 랜덤 페이지 번호 (0~19)
  const page = Math.floor(Math.random() * 20); // 0부터 19 사이의 랜덤 값
  const size = 50; // 한 페이지당 50개 조회

  // **홈 조회**
  const homeResponse = http.get(`${baseUrl}/api/home?page=${page}&size=${size}`);
  Response_Time.add(homeResponse.timings.duration);
  console.log('홈 응답 상태 코드:', homeResponse.status);
  check(homeResponse, {
    '홈 응답 상태는 200': (res) => res.status === 200,
    '홈 응답 본문 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });

  // 예약 전송을 위한 데이터
  const memberId = `user${__VU}`; // Virtual User ID (user1, user2, ..., user500)
  const reservationBody = JSON.stringify({
    memberId: memberId,
    date: '2024-12-08',
    timeSlot: '23:00',
    storeId: '1001',
  });
  const reservationHeaders = { 'Content-Type': 'application/json' };
  // **예약 신청**
  const reservationResponse = http.post(`${baseUrl}/api/reservation/enter`, reservationBody, { headers: reservationHeaders });
  Response_Time.add(reservationResponse.timings.duration);
  console.log('예약 신청 응답 상태 코드:', reservationResponse.status);
  console.log('예약 신청 응답 본문:', reservationResponse.body);
  check(reservationResponse, {
    '예약 신청 응답 상태는 200': (res) => res.status === 200,
    '예약 신청 응답 문자열 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });

  // **현재 순번 확인**
  const queueMemberId = `user1`;
  const date = '2024-12-08';
  const timeSlot = '20:00';
  const storeId = '1001'; 
  const queueStatusUrl = `${baseUrl}/api/reservation/queue-status?memberId=${memberId}&date=${date}&timeSlot=${timeSlot}&storeId=${storeId}`;
  const queueStatusResponse = http.get(queueStatusUrl);
  Response_Time.add(queueStatusResponse.timings.duration);
  console.log('현재 순번 확인 응답 상태 코드:', queueStatusResponse.status);
  console.log('현재 순번 확인 응답 본문:', queueStatusResponse.body);
  check(queueStatusResponse, {
    '현재 순번 확인 응답 상태는 200': (res) => res.status === 200,
    '현재 순번 응답 문자열 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });

  // 사용자 동작 시뮬레이션
  sleep(1); // 1초 대기
}
```
- 팝업스토어 redis 적용 전
![image (1)](https://github.com/user-attachments/assets/d5420f13-2aec-4c18-a604-157ba67845ab)

- 팝업스토어 조회 redis 적용 후
![image (2)](https://github.com/user-attachments/assets/38278116-9eb6-44a1-a210-00b830565354)
- 응답 성공률: 100%
- `http_req_duration` 평균 요청 시간: 18.28ms
- `http_reqs` 초당 요청 처리량: 초당 95건
- `iteration_duration` 평균 반복 시간: 1.05s


### **2. 부하 테스트**
**목적**: 시스템의 최대 처리량을 파악하고 성능 병목 현상을 진단.
1. **가상 사용자 수**: 5000명
    - 예상되는 최대 사용량 근처에서 테스트.
2. **지속시간**: 10분
    - 실제 사용 환경을 가정해 안정성을 확인.
3. **피크타임, 램프업, 다운 여부 및 조건**:
    - 램프업: 2분 동안 0명 → 5000명
    - 피크타임: 6분
    - 다운: 2분 동안 5000명 → 0명
4. **사용 시나리오**:
    - **팝업 스토어 정보 전체 조회**: 데이터 조회 요청이 많은 상황에서 성능 확인.
    - **검색**: 다수의 검색 요청을 처리할 수 있는지 검증.
    - **팝업 스토어 정보 상세 조회**: 여러 데이터 요청에 대한 복원력 확인.
    - **예약 신청**: 동시다발적인 예약 생성 요청 처리 여부 확인.
  
코드
```javascript
import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend } from 'k6/metrics';

// Custom metrics
const Response_Time = new Trend('Response_Time');

export const options = {
  stages: [
    { duration: '1m', target: 50 }, 
    { duration: '1m', target: 50 },
    { duration: '1m', target: 0 },
  ],
};

export default function () {
  const baseUrl = 'http://west-somsom-alb-1941268033.ap-northeast-2.elb.amazonaws.com';

  // 랜덤 페이지 번호 (0~19)
  const page = Math.floor(Math.random() * 20); // 0부터 19 사이의 랜덤 값
  const size = 50; // 한 페이지당 50개 조회

  // **홈 조회**
  const homeResponse = http.get(`${baseUrl}/api/home?page=${page}&size=${size}`);
  Response_Time.add(homeResponse.timings.duration);
  console.log('홈 응답 상태 코드:', homeResponse.status);
  check(homeResponse, {
    '홈 응답 상태는 200': (res) => res.status === 200,
    '홈 응답 본문 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });

  // 예약 전송을 위한 데이터
  const memberId = `user${__VU}`; // Virtual User ID (user1, user2, ..., user500)
  const reservationBody = JSON.stringify({
    memberId: memberId,
    date: '2024-12-08',
    timeSlot: '23:00',
    storeId: '1001',
  });
  const reservationHeaders = { 'Content-Type': 'application/json' };
  // **예약 신청**
  const reservationResponse = http.post(`${baseUrl}/api/reservation/enter`, reservationBody, { headers: reservationHeaders });
  Response_Time.add(reservationResponse.timings.duration);
  console.log('예약 신청 응답 상태 코드:', reservationResponse.status);
  console.log('예약 신청 응답 본문:', reservationResponse.body);
  check(reservationResponse, {
    '예약 신청 응답 상태는 200': (res) => res.status === 200,
    '예약 신청 응답 문자열 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });

  // **현재 순번 확인**
  const queueMemberId = `user1`;
  const date = '2024-12-08';
  const timeSlot = '20:00';
  const storeId = '1001'; 
  const queueStatusUrl = `${baseUrl}/api/reservation/queue-status?memberId=${memberId}&date=${date}&timeSlot=${timeSlot}&storeId=${storeId}`;
  const queueStatusResponse = http.get(queueStatusUrl);
  Response_Time.add(queueStatusResponse.timings.duration);
  console.log('현재 순번 확인 응답 상태 코드:', queueStatusResponse.status);
  console.log('현재 순번 확인 응답 본문:', queueStatusResponse.body);
  check(queueStatusResponse, {
    '현재 순번 확인 응답 상태는 200': (res) => res.status === 200,
    '현재 순번 응답 문자열 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });

  // 사용자 동작 시뮬레이션
  sleep(1); // 1초 대기
}
```
![image (3)](https://github.com/user-attachments/assets/ea51a19e-98a8-42ee-972b-8aaaa8e814aa)
- 응답 성공률: 99.98%
- `http_req_duration` 평균 요청 시간: 31.28ms
- `http_reqs` 초당 요청 처리량: 초당 362건
- `iteration_duration` 평균 반복 시간: 31.28s
  
### **3. 스파이크 테스트**
**목적**: 짧은 시간 동안 갑작스러운 사용량 증가에 대한 시스템의 복원력 확인.

1. **가상 사용자 수**: 10000명
    - 극단적인 부하 상황을 가정.
2. **지속시간**: 3분
    - 단시간 내 급격한 부하 처리 및 안정성 확인.
3. **피크타임, 램프업, 다운 여부 및 조건**:
    - 램프업: 1분 동안 0명 → 10000명
    - 피크타임: 1분
    - 다운: 1분 동안 10000명 → 0명
4. **사용 시나리오**:
    - **검색**: 갑작스러운 검색 요청 폭주 시 성능 확인.
    - **팝업 스토어 정보 상세 조회**: 여러 데이터 요청에 대한 복원력 확인.
    - **예약 신청**: 갑작스러운 예약 요청 폭주 시 안정성 검증.

코드
```javascript
import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend } from 'k6/metrics';
// Custom metrics
const Response_Time = new Trend('Response_Time');
const THRESHOLD = 8000; // 지연 시간 임계값 (밀리초 단위, 2초)
export const options = {
  stages: [
    { duration: '1m', target: 5000 },
    { duration: '1m', target: 10000 },
    { duration: '1m', target: 0 },
  ],
};
export default function () {
  const baseUrl = 'http://west-somsom-alb-1941268033.ap-northeast-2.elb.amazonaws.com';

  // **홈 조회**
  // 랜덤 페이지 번호 (0~50)
  const page = Math.floor(Math.random() * 50);
  const homeResponse = http.get(`${baseUrl}/api/home?page=${page}`);
  Response_Time.add(homeResponse.timings.duration);
  if (homeResponse.timings.duration > THRESHOLD) {
    console.log(`홈 조회 지연 발생: ${homeResponse.timings.duration}ms, 상태 코드: ${homeResponse.status}`);
  }
  const homeCheck = check(homeResponse, {
    '홈 응답 상태는 200': (res) => res.status === 200,
    '홈 응답 본문 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });
  if (!homeCheck) {
    console.log('홈 조회 오류: 상태 코드:', homeResponse.status, '본문:', homeResponse.body);
  }

  // **검색 API**
  const searchName = 'Maxidex';
  const searchLoc = encodeURIComponent('3 Declaration Road');
  const searchResponse = http.get(`${baseUrl}/api/search?name=${searchName}&loc=${searchLoc}`);
  Response_Time.add(searchResponse.timings.duration);
  if (searchResponse.timings.duration > THRESHOLD) {
    console.log(`검색 지연 발생: ${searchResponse.timings.duration}ms, 상태 코드: ${searchResponse.status}`);
  }
  const searchCheck = check(searchResponse, {
    '검색 응답 상태는 200': (res) => res.status === 200,
    '검색 응답 본문 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });
  if (!searchCheck) {
    console.log('검색 오류: 상태 코드:', searchResponse.status, '본문:', searchResponse.body);
  }

  // **상세조회 API**
  const storeId = 2000;
  const storeDetailResponse = http.get(`${baseUrl}/api/store/${storeId}`);
  Response_Time.add(storeDetailResponse.timings.duration);
  if (storeDetailResponse.timings.duration > THRESHOLD) {
    console.log(`상세조회 지연 발생: ${storeDetailResponse.timings.duration}ms, 상태 코드: ${storeDetailResponse.status}`);
  }
  const storeDetailCheck = check(storeDetailResponse, {
    '상세조회 응답 상태는 200': (res) => res.status === 200,
    '상세조회 응답 본문 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });
  if (!storeDetailCheck) {
    console.log('상세조회 오류: 상태 코드:', storeDetailResponse.status, '본문:', storeDetailResponse.body);
  }

  // 예약 전송을 위한 데이터
  const memberId = `user${__VU}`;
  const reservationBody = JSON.stringify({
    memberId: memberId,
    date: '2024-12-08',
    timeSlot: '24:00',
    storeId: '1001',
  });
  const reservationHeaders = { 'Content-Type': 'application/json' };
  // **예약 신청**
  const reservationResponse = http.post(`${baseUrl}/api/reservation/enter`, reservationBody, { headers: reservationHeaders });
  Response_Time.add(reservationResponse.timings.duration);
  if (reservationResponse.timings.duration > THRESHOLD) {
    console.log(`예약 신청 지연 발생: ${reservationResponse.timings.duration}ms, 상태 코드: ${reservationResponse.status}`);
  }
  const reservationCheck = check(reservationResponse, {
    '예약 신청 응답 상태는 200': (res) => res.status === 200,
    '예약 신청 응답 문자열 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });
  if (!reservationCheck) {
    console.log('예약 신청 오류: 상태 코드:', reservationResponse.status, '본문:', reservationResponse.body);
  }

  // **현재 순번 확인**
  const queueMemberId = `user1`;
  const date = '2024-12-08';
  const timeSlot = '24:00';
  const res_storeId = '1001';
  const queueStatusUrl = `${baseUrl}/api/reservation/queue-status?memberId=${queueMemberId}&date=${date}&timeSlot=${timeSlot}&storeId=${res_storeId}`;
  const queueStatusResponse = http.get(queueStatusUrl);
  Response_Time.add(queueStatusResponse.timings.duration);
  if (queueStatusResponse.timings.duration > THRESHOLD) {
    console.log(`현재 순번 확인 지연 발생: ${queueStatusResponse.timings.duration}ms, 상태 코드: ${queueStatusResponse.status}`);
  }
  const queueStatusCheck = check(queueStatusResponse, {
    '현재 순번 확인 응답 상태는 200': (res) => res.status === 200,
    '현재 순번 응답 문자열 존재 여부 확인': (res) => res.body && res.body.trim().length > 0,
  });
  if (!queueStatusCheck) {
    console.log('현재 순번 확인 오류: 상태 코드:', queueStatusResponse.status, '본문:', queueStatusResponse.body);
  }

  // 사용자 동작 시뮬레이션
  sleep(1); // 1초 대기
}
```
- Redis Pub/Sub 적용 전
![image (4)](https://github.com/user-attachments/assets/07ba003d-ee74-43c5-8465-fde6bf2fb35f)
- Redis Pub/Sub 적용 후
![image (5)](https://github.com/user-attachments/assets/04c2fccc-db3c-40b5-9bfd-01c55a5af4fa)
- 응답 속도: 최대 응답 시간 및 응답 수신 시간
  - http_req_receiving 크게 개선 : 절반 가량 감소 (1m0s -> 55.99s)
   ![image (2) (1)](https://github.com/user-attachments/assets/87efffe2-1aed-4bd8-b55f-fcd2620cf333)
- 데이터 전송량: 서버-클라이언트 간 전송 데이터 양 감소
  - Redis 적용으로 인해 불필요한 데이터 전송 감소
    ![image (6)](https://github.com/user-attachments/assets/e0c67a35-6bee-4f12-9c16-267ba2f01f11)

## **개선할 점**
#### 1. 팝업 스토어 전체 조회 부분 Redis 사용
- 도입 배경
  - 기존 DB 직접 조회 방식에서, 데이터 양이 **"connection timeout"** 오류 발생:
        `read tcp 192.168.1.6:58959->15.164.35.224:80: wsarecv: A connection attempt failed because the connected party did not properly respond after a period of time, or established connection failed because connected host has failed to respond."`
  - 원인은 대량 조회로 인한 DB 부하로 추정, 이를 해결하기 위해 캐시(Redis) 도입
    - **변경 내용:** 팝업 스토어 등록 시 캐시에 저장 → 전체 조회는 캐시에서 처리
- 문제점
  - 캐시 미스 시 DB 조회 및 캐시 저장 과정 추가로, 단순 DB 조회보다 비효율적
- 개선 방향
   - 캐시 전략 최적화 필요:
      - 자주 조회되는 데이터 우선 캐싱
      - 캐시 적중률 향상을 위한 TTL(Time-to-Live) 및 데이터 구조 개선
#### 2. EC2 접근을 위해 Bastion host 사용
- Bastion host 대신 System Manager Session Manager 도입을 통해 보다 더 안전한 접근 관리 필요
#### 3. CI/CD 파이프라인 부재
- 빌드 파일 생성 및  Filezilla를 통한 수동 배포 진행
- 적절한 CI/CD 파이프라인 구축 필요
  - github action + ECR 등의 파이프라인 구축 가능
#### 4. Auto Scaling 정책 부재
- 정책 설정의 필요성
   - 성능 테스트 시 인스턴스의 컴퓨터 자원에 따라 결과가 다르게 나타남
   - Auto Scaling Group의 CPUUtilization을 경보로 설정한 단계 크기 정책 필요