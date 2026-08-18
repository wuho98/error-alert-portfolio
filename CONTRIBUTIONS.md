# 개인 기여 범위

이 문서는 원본 팀 저장소의 커밋 작성자와 실제 변경 내용을 기준으로 정리한 개인 기여 범위입니다. 공개본은 내부 문서와 원본 PR 이력을 제외한 스냅샷이므로, 아래 경로를 통해 구현과 테스트를 직접 확인할 수 있습니다.

## 오류 급증 감지 흐름

- [RedisErrorRateCounter.java](src/main/java/com/wuho/erroralert/service/RedisErrorRateCounter.java): Redis Lua script와 ZSET을 이용한 최근 60초 슬라이딩 윈도우 카운터
- [ErrorEventSavedListener.java](src/main/java/com/wuho/erroralert/service/ErrorEventSavedListener.java): 오류 저장 커밋 이후 카운트 증가, 임계값 판정, cooldown 선점, 알림 생성 연결
- [RedisErrorRateCounterTest.java](src/test/java/com/wuho/erroralert/service/RedisErrorRateCounterTest.java): 키 구성, 윈도우 계산, Lua 실행 결과와 예외 흐름 검증

## 중복 알림 억제

- [AlertCooldownService.java](src/main/java/com/wuho/erroralert/service/AlertCooldownService.java): Redis `SET NX`와 TTL 기반 cooldown 선점
- [AlertLogCreator.java](src/main/java/com/wuho/erroralert/service/AlertLogCreator.java): 별도 트랜잭션에서 AlertLog를 저장하고 DB 중복 충돌을 이미 처리된 알림으로 전환
- [AlertCooldownServiceTest.java](src/test/java/com/wuho/erroralert/service/AlertCooldownServiceTest.java): cooldown 키와 선점 결과 검증
- [AlertCooldownConcurrencyPerformanceTest.java](src/performanceTest/java/com/wuho/erroralert/service/AlertCooldownConcurrencyPerformanceTest.java): 동시 요청에서 단일 선점이 유지되는지 실제 Redis로 검증

## 원자성·동시성 검증

- [RedisCounterLuaAtomicityPerformanceTest.java](src/performanceTest/java/com/wuho/erroralert/service/RedisCounterLuaAtomicityPerformanceTest.java): 카운터 갱신과 만료 설정이 Lua 안에서 함께 수행되는지 실제 Redis로 검증
- [ErrorRateCounterStrategyPerformanceTest.java](src/performanceTest/java/com/wuho/erroralert/service/ErrorRateCounterStrategyPerformanceTest.java): 카운터 전략별 부하 특성을 비교하는 선택 실행 테스트

## API 사용자 흐름 검증

- [api.spec.ts](tests/e2e/api.spec.ts): 프로젝트 생성 → API 키 발급 → 오류 전송 → 최근 오류 조회 흐름과 잘못된 API 키 거부를 Playwright로 검증

## 기여 구분 원칙

- 위 목록은 개인이 구현하거나 주도적으로 검증한 범위만 적었습니다.
- 도메인 모델, 조회 API, Webhook, 모니터링 등 나머지 영역에는 다른 팀원의 기여가 포함되어 있습니다.
- 공개본을 위한 패키지명 변경과 내부 식별자 제거는 기능 변경이 아닌 공개 정리 작업입니다.
