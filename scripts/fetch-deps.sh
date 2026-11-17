#!/bin/sh
# 把规范 2.4 要求但本地仓库缺失的构件补进本地 Maven 仓。
# 用法（宿主机不能出网、容器可以）：
#   docker run --rm \
#     -v D:\develop\apache-maven-3.9.11\mvn_repo:/root/.m2/repository \
#     -v ${PWD}/scripts:/scripts:ro \
#     maven:3.9-eclipse-temurin-17 sh /scripts/fetch-deps.sh
set -e

ARTIFACTS="
org.flywaydb:flyway-core:10.10.0
org.flywaydb:flyway-database-postgresql:10.10.0
org.testcontainers:testcontainers:1.19.7
org.testcontainers:postgresql:1.19.7
org.testcontainers:junit-jupiter:1.19.7
org.testcontainers:jdbc:1.19.7
org.testcontainers:database-commons:1.19.7
com.github.docker-java:docker-java-api:3.3.6
com.github.docker-java:docker-java-transport:3.3.6
com.github.docker-java:docker-java-transport-zerodep:3.3.6
org.rnorth.duct-tape:duct-tape:1.0.8
org.hamcrest:hamcrest-core:2.2
net.javacrumbs.shedlock:shedlock-spring:5.13.0
net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.13.0
"

for a in $ARTIFACTS; do
    echo "== $a"
    mvn -B -q dependency:get -Dartifact="$a" || mvn -B -q dependency:get -Dartifact="$a:pom" || true
done

# 清掉来源标记，否则离线构建会因“不是从当前镜像下载”而拒绝使用
find /root/.m2/repository -name '_remote.repositories' -delete 2>/dev/null || true
find /root/.m2/repository -name '*.lastUpdated' -delete 2>/dev/null || true

echo "ALL_FETCHED"
