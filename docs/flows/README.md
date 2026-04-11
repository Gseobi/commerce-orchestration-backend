# Flow Notes

Order Lifecycle Flow 다이어그램은 추후 추가될 예정입니다.

현재 초기 스캐폴드 기준 flow는 다음 순서를 가정합니다.

1. 주문 생성
2. orchestration 시작
3. payment 처리
4. settlement 요청
5. notification 요청
6. 최종 완료 또는 후속 보상 분기

현재 버전은 happy path 중심 초기 구조이며, 아래 항목은 후속 작업으로 남겨 둡니다.

- retry / backoff
- idempotency 보장
- compensation 정책
- callback / consumer 기반 비동기 후속 처리
