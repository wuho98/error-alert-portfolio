# Playwright API E2E

프로젝트 생성, API 키 발급, 오류 수신, 최근 오류 조회와 잘못된 API 키 거부 흐름을 실제 HTTP 요청으로 검증합니다.

## 실행 방법

```bash
cd tests/e2e
npm ci
npx playwright install --with-deps chromium   # 최초 1회
BASE_URL=http://localhost:8080 npm test
```

테스트를 실행하기 전에 애플리케이션과 MySQL·Redis가 떠 있어야 합니다.
