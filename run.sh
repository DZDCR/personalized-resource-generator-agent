#!/bin/bash
# 学习多智能体系统 - Linux/Mac 一键启动脚本
# 前提：已安装 JDK 21、Maven 3.9+、Node 20+、Docker

set -e

echo "[1/4] 启动基础依赖..."
docker compose -f docker/docker-compose.yml up -d postgres redis rabbitmq neo4j qdrant

echo "[2/4] 等待数据库就绪..."
sleep 10

echo "[3/4] 启动后端 (端口 8080)..."
cd backend
export OPENAI_API_KEY=${OPENAI_API_KEY:-sk-placeholder}
mvn spring-boot:run &
BACKEND_PID=$!
cd ..

echo "[4/4] 启动前端 (端口 5173)..."
cd frontend
npm install && npm run dev &
FRONTEND_PID=$!
cd ..

echo ""
echo "========================================"
echo " 后端: http://localhost:8080"
echo " 前端: http://localhost:5173"
echo " 基础设施: docker compose ps"
echo "========================================"
echo ""
echo "停止: kill $BACKEND_PID $FRONTEND_PID && docker compose -f docker/docker-compose.yml down"

wait