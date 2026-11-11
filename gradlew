#!/bin/sh
# 精简版 Gradle Wrapper 启动脚本（等价于官方 gradlew 的核心逻辑）。
# 如需官方完整脚本，在装有 Gradle 的机器上执行：gradle wrapper --gradle-version 9.6.0

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "错误：未找到 java。请安装 JDK 17 或以上，并设置 JAVA_HOME。" >&2
    exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
