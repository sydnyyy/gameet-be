## Gameet 프로젝트 소개

![](/img/gameet-info.png)

Gameet는 선호하는 게임 장르와 스타일이 맞는 게이머를 실시간 매칭해주는 서비스입니다.
- 매칭된 친구와 채팅으로 어떤 게임을 할지 이야기 나눌 수 있습니다.
- 약속 시각을 설정하면, 약속 10분 전 웹 알림과 이메일 알림이 발송되어 약속을 놓치지 않고 게임을 즐길 수 있습니다.

### 기술 스택
- Java 21
- Spring Boot (Security, WebSocket): JWT 기반 인증/인가 처리 및 실시간 매칭, 채팅 기능 구현
- MySQL: 매칭 완료 데이터 및 사용자 정보 관리
- Redis: 인증 코드 및 매칭 처리 데이터 관리
- AWS SES + JavaMailSender: 알림 이메일 발송

<br>

## 이슈 해결

### WebSocket 중복 연결

❗️ 원인
- FE useEffect 의존성 배열에 pathname, handleMatchNotification 등이 포함되어, 컴포넌트 렌더링마다 재실행
- 그 결과, 같은 브라우저 탭에서 **기존 연결 해제 없이 WebSocket 중복 연결 발생**

✅ 해결
- 정책: 브라우저 탭 기준 WebSocket 세션 1개만 연결 가능
- 방법: 자체 세션 저장소 생성 및 HandshakeInterceptor 단계에서 중복 세션 감지 시 연결 거부

### 이메일 알림 지연

❗️ 원인
- JavaMailSender 사용 시 1건 발송당 4s 소요
- 처리량 증가 시 전체 전송 속도 저하

✅ 해결

- 그룹 단위로 비동기 처리
- AWS SES Bulk 전송으로 0.3s 소요 및 AWS SES API 호출 최소화