@echo off
REM 学习多智能体系统 - Windows 一键启动脚本
REM 前提：已安装 JDK 21、Maven 3.9+、Node 20+、Docker

echo [1/4] 启动基础依赖...
docker compose -f docker\docker-compose.yml up -d postgres redis rabbitmq neo4j qdrant

echo [2/4] 等待数据库就绪...
timeout /t 10 /nobreak > nul

echo [3/4] 启动后端 (端口 8080)...
cd backend
start "learning-backend" cmd /k "set OPENAI_API_KEY=sk-xxxx && mvn spring-boot:run"
cd ..

echo [4/4] 启动前端 (端口 5173)...
cd frontend
start "learning-frontend" cmd /k "npm install && npm run dev"
cd ..

echo.
echo ========================================
echo  后端: http://localhost:8080
echo  前端: http://localhost:5173
echo  基础设施: docker compose ps
echo  Swagger: http://localhost:8080/swagger-ui.html (如已配置)
echo ========================================
pause