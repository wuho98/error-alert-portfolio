import { test, expect } from "@playwright/test";

type ApiResponse<T> = {
  data: T;
};

type ProjectCreateResponse = {
  projectId: number;
  name: string;
};

type ProjectApiKeyCreateResponse = {
  projectId: number;
  apiKey: string;
};

type ReceiveErrorEventResponse = {
  eventId: number;
  projectId: number;
  errorCode: string;
};

type RecentErrorEventResponse = {
  eventId: number;
  errorCode: string;
  message: string;
  occurredAt: string;
};

type RecentErrorEventsResponse = {
  totalElements: number;
  content: RecentErrorEventResponse[];
};

type ApiErrorResponse = {
  code: string;
  message: string;
};

const adminHeaders = {
  "X-User-Id": "playwright-admin",
  "X-Role": "ADMIN",
};

const operatorHeaders = {
  "X-User-Id": "playwright-operator",
  "X-Role": "OPERATOR",
};

test("관리자가 연동을 준비하면 개발자가 보낸 오류를 운영자가 최근 목록에서 확인한다", async ({
  request,
}) => {
  const scenarioId = `${Date.now()}-${test.info().workerIndex}`;
  const projectName = `playwright-e2e-${scenarioId}`;
  const errorMessage = `결제 승인 처리 실패 (${scenarioId})`;
  const errorCode = "PAYMENT.APPROVAL_PROCESSING_FAILED";
  const occurredAt = new Date().toISOString();
  let projectId = 0;

  await test.step("관리자가 프로젝트를 생성한다", async () => {
    const response = await request.post("/api/v1/projects", {
      headers: adminHeaders,
      data: { name: projectName },
    });

    expect(
      response.status(),
      `프로젝트 생성이 실패했다: HTTP ${response.status()} ${await response.text()}`,
    ).toBe(201);

    const body = (await response.json()) as ApiResponse<ProjectCreateResponse>;
    expect(body.data.projectId, "생성 응답에 프로젝트 ID가 없다").toBeGreaterThan(0);
    expect(body.data.name, "생성된 프로젝트 이름이 요청과 다르다").toBe(projectName);
    projectId = body.data.projectId;
  });

  let apiKey = "";
  await test.step("관리자가 연동용 API 키를 발급한다", async () => {
    const response = await request.post(`/api/v1/projects/${projectId}/api-keys`, {
      headers: adminHeaders,
    });

    expect(
      response.status(),
      `API 키 발급이 실패했다: HTTP ${response.status()} ${await response.text()}`,
    ).toBe(201);

    const body = (await response.json()) as ApiResponse<ProjectApiKeyCreateResponse>;
    expect(body.data.projectId, "API 키가 다른 프로젝트에 발급됐다").toBe(projectId);
    expect(body.data.apiKey, "발급 응답에 API 키가 없다").toBeTruthy();
    apiKey = body.data.apiKey;
  });

  let receivedEventId = 0;
  await test.step("연동 개발자가 실제 오류 이벤트를 전송한다", async () => {
    const response = await request.post("/api/v1/errors", {
      headers: { "X-Api-Key": apiKey },
      data: {
        errorCode,
        message: errorMessage,
        occurredAt,
      },
    });

    expect(
      response.status(),
      `오류 이벤트 수신이 실패했다: HTTP ${response.status()} ${await response.text()}`,
    ).toBe(201);

    const body = (await response.json()) as ApiResponse<ReceiveErrorEventResponse>;
    expect(body.data.eventId, "수신 응답에 오류 이벤트 ID가 없다").toBeGreaterThan(0);
    expect(body.data.projectId, "오류 이벤트가 다른 프로젝트에 저장됐다").toBe(projectId);
    expect(body.data.errorCode, "저장된 오류 코드가 전송 값과 다르다").toBe(errorCode);
    receivedEventId = body.data.eventId;
  });

  await test.step("운영자가 프로젝트의 최근 오류 목록에서 방금 보낸 오류를 확인한다", async () => {
    const response = await request.get("/api/v1/errors", {
      headers: operatorHeaders,
      params: {
        projectId,
        errorCode,
        page: 0,
        size: 20,
      },
    });

    expect(
      response.status(),
      `최근 오류 목록 조회가 실패했다: HTTP ${response.status()} ${await response.text()}`,
    ).toBe(200);

    const body = (await response.json()) as ApiResponse<RecentErrorEventsResponse>;
    const receivedEvent = body.data.content.find(({ eventId }) => eventId === receivedEventId);

    expect(body.data.totalElements, "오류를 전송했지만 최근 오류 건수가 0이다").toBeGreaterThan(0);
    expect(receivedEvent, "방금 전송한 오류가 최근 오류 목록에 없다").toBeDefined();
    expect(receivedEvent?.message, "목록의 오류 메시지가 전송 값과 다르다").toBe(errorMessage);
    expect(receivedEvent?.occurredAt, "목록의 발생 시각이 전송 값과 다르다").toBe(occurredAt);
  });
});

test("연동 개발자가 잘못된 API 키로 오류를 보내면 인증이 거부된다", async ({ request }) => {
  const response = await request.post("/api/v1/errors", {
    headers: { "X-Api-Key": "invalid-playwright-api-key" },
    data: {
      errorCode: "PAYMENT.APPROVAL_PROCESSING_FAILED",
      message: "잘못된 API 키 인증 실패 확인",
      occurredAt: new Date().toISOString(),
    },
  });
  const responseBody = await response.text();

  expect(
    response.status(),
    `잘못된 API 키가 거부되지 않았다: HTTP ${response.status()} ${responseBody}`,
  ).toBe(401);

  const error = JSON.parse(responseBody) as ApiErrorResponse;
  expect(error.code, "잘못된 API 키 응답의 인증 오류 코드가 A001이 아니다").toBe("A001");
});
