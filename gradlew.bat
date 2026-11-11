@rem 精简版 Gradle Wrapper 启动脚本（Windows）。
@rem 如需官方完整脚本，在装有 Gradle 的机器上执行：gradle wrapper --gradle-version 9.6.0

@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java.exe
)

"%JAVACMD%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
    "-Dorg.gradle.appname=gradlew" ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain %*

endlocal
