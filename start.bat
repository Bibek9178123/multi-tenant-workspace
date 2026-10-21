@echo off
title NexSpace Multi-Tenant Workspace SaaS
echo ========================================================
echo   Starting NexSpace Multi-Tenant Workspace Application
echo   Location: C:\Users\bibek\Desktop\multi-tenant-workspace
echo   Backend: Spring Boot 3 (Port 8085)
echo   Frontend: HTML5, CSS3, JavaScript (http://localhost:8085)
echo ========================================================
echo.

IF EXIST target\multi-tenant-workspace-1.0.0.jar (
    echo Launching pre-built JAR package...
    java -jar target\multi-tenant-workspace-1.0.0.jar
) ELSE (
    echo Building and launching with Maven...
    "C:\Users\bibek\Desktop\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd" spring-boot:run
)

pause
