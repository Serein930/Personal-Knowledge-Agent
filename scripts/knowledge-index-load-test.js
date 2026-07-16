import http from "k6/http";
import { check, sleep } from "k6";

// 将 BASE_URL 指向多实例负载均衡入口，即可验证实例间租约竞争与接口稳定性。
const baseUrl = __ENV.BASE_URL || "http://localhost:8081";
const workspaceId = __ENV.WORKSPACE_ID || "1";
const userId = __ENV.USER_ID || "1";

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || "60s",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000"],
  },
};

export default function () {
  const headers = { "X-Demo-User-Id": userId };
  const response = http.post(
    `${baseUrl}/api/v1/workspaces/${workspaceId}/knowledge/index-operations/outbox/process-once`,
    null,
    { headers },
  );
  check(response, {
    "索引消费接口成功": (result) => result.status === 200,
  });
  sleep(0.2);
}
