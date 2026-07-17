import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8081";
const workspaceId = __ENV.WORKSPACE_ID || "1";
const accessToken = __ENV.ACCESS_TOKEN || "";
const headers = {
  "Content-Type": "application/json",
  ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
};

const searchDuration = new Trend("agentmind_search_duration", true);
const ragDuration = new Trend("agentmind_rag_duration", true);

export const options = {
  scenarios: {
    health_probe: {
      executor: "constant-arrival-rate",
      exec: "healthProbe",
      rate: Number(__ENV.HEALTH_RATE || 2),
      timeUnit: "1s",
      duration: __ENV.DURATION || "2m",
      preAllocatedVUs: 2,
      maxVUs: 5,
    },
    knowledge_workload: {
      executor: "ramping-vus",
      exec: "knowledgeWorkload",
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP_UP || "30s", target: Number(__ENV.VUS || 30) },
        { duration: __ENV.DURATION || "2m", target: Number(__ENV.VUS || 30) },
        { duration: "30s", target: 0 },
      ],
      gracefulRampDown: "20s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{endpoint:health}": ["p(95)<200", "p(99)<500"],
    agentmind_search_duration: ["p(95)<800", "p(99)<1500"],
    agentmind_rag_duration: ["p(95)<3000", "p(99)<6000"],
    checks: ["rate>0.99"],
  },
};

export function healthProbe() {
  const response = http.get(`${baseUrl}/actuator/health/readiness`, {
    tags: { endpoint: "health" },
  });
  check(response, { "就绪探针可用": (result) => result.status === 200 });
}

export function knowledgeWorkload() {
  const searchResponse = http.post(
    `${baseUrl}/api/v1/workspaces/${workspaceId}/knowledge/search`,
    JSON.stringify({ query: "Java Agent 知识检索", topK: 5 }),
    { headers, tags: { endpoint: "search" } },
  );
  searchDuration.add(searchResponse.timings.duration);
  check(searchResponse, {
    "知识检索成功": (result) => result.status === 200,
    "知识检索未被限流": (result) => result.status !== 429,
  });

  const ragResponse = http.post(
    `${baseUrl}/api/v1/workspaces/${workspaceId}/rag/chat`,
    JSON.stringify({ question: "根据知识库解释 Agent 工具调用", topK: 5 }),
    { headers, tags: { endpoint: "rag" }, timeout: "15s" },
  );
  ragDuration.add(ragResponse.timings.duration);
  check(ragResponse, {
    "检索增强生成成功": (result) => result.status === 200,
    "检索增强生成未被限流": (result) => result.status !== 429,
  });
  sleep(Number(__ENV.THINK_TIME_SECONDS || 1));
}

export function handleSummary(data) {
  // 原始指标保留给容量报告和不同版本之间的可重复比较，不在脚本中伪造测试结论。
  return {
    "performance-summary.json": JSON.stringify(data, null, 2),
    stdout: "性能门禁完成，原始结果已写入 performance-summary.json\n",
  };
}
