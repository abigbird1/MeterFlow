#!/bin/bash
# MeterFlow 全栈一键启动（含基础设施重试）：Docker → Nacos/MQ → 五服务 → 就绪探测
# 用法：bash scripts/start-all.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== 1) Docker Desktop =="
if ! docker ps >/dev/null 2>&1; then
  "/d/develop/Docker/Program/Docker Desktop.exe" & sleep 60
  for i in 1 2 3; do docker ps >/dev/null 2>&1 && break; sleep 10; done
fi
docker ps >/dev/null 2>&1 && echo "Docker OK" || { echo "Docker 未就绪"; exit 1; }

echo "== 2) Nacos / RocketMQ 容器 =="
cd "$ROOT/deploy"
docker compose up -d nacos rocketmq-namesrv rocketmq-broker 2>&1 | tail -3
for i in $(seq 1 12); do
  curl -s -m 3 "http://localhost:8848/nacos/v1/console/health/readiness" | grep -q OK && break
  sleep 5
done
echo "Nacos ready"

echo "== 2.5) 端口冲突治理（zhitan-api restart=always 会抢 8080） =="
docker update --restart=no zhitan-api 2>/dev/null || true
docker stop zhitan-api 2>/dev/null || true
sleep 2

echo "== 3) 五服务（失败自动重试 3 次；依赖 Nacos 先行） =="
cd "$ROOT/server"
start_svc() {  # start_svc <name> <port>
  for attempt in 1 2 3; do
    (java -jar meterflow-$1/target/meterflow-$1-1.0.0-SNAPSHOT.jar > /tmp/$1.log 2>&1 &)
    for i in $(seq 1 10); do
      sleep 5
      curl -s -m 3 -o /dev/null "http://localhost:$2/actuator/health" 2>/dev/null && { echo "$1 UP (尝试 $attempt)"; return 0; }
    done
    netstat -ano | grep LISTENING | grep ":$2 " | awk '{print $NF}' | sort -u | head -1 | xargs -r taskkill //F //PID >/dev/null 2>&1
    echo "$1 尝试 $attempt 失败，重试…"
  done
  echo "$1 启动失败"; return 1
}
start_svc platform 8081
start_svc billing  8082
start_svc equity   8083
start_svc finance  8084
start_svc gateway  8080
# 网关绑定验证：/auth/login 应 200（若 401=8080 被其它 Sa-Token 应用占用）
for i in $(seq 1 6); do
  code=$(curl -s -m 3 -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/auth/login     -H "Content-Type: application/json" -d '{"email":"x@x.cn","password":"x","portal":"t"}')
  [ "$code" = "200" ] && break
  echo "网关 login 返回 $code（可能 8080 被占），重试…"; sleep 3
done

echo "== 4) 就绪探测 =="
for p in 8080 8081 8082 8083 8084; do
  echo -n "$p: "; curl -s -m 3 http://localhost:$p/actuator/health 2>/dev/null || echo DOWN
done
echo "完成：单端口入口 http://localhost:8080"
