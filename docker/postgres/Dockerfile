# 中文分词增强镜像：pgvector 16 + zhparser（SCWS 1.2.3）
#
# 依赖环境必须可从零重建（规范 15.1），而 pgvector 官方镜像不含中文分词扩展，
# 因此在此镜像中从源码编译 SCWS 与 zhparser，数据库初始化脚本即可直接使用 crag_cn。
#
# 构建：docker compose -f docker/compose.yaml build postgres
FROM pgvector/pgvector:pg16

ENV DEBIAN_FRONTEND=noninteractive \
    SCWS_HOME=/usr/local \
    LD_LIBRARY_PATH=/usr/local/lib

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        build-essential postgresql-server-dev-16 wget ca-certificates bzip2; \
    # SCWS 1.2.3：zhparser 的分词词典与底层库
    cd /tmp; \
    wget -q -T 60 http://www.xunsearch.com/scws/down/scws-1.2.3.tar.bz2; \
    tar xjf scws-1.2.3.tar.bz2; \
    cd scws-1.2.3; \
    ./configure --prefix=/usr/local; \
    make -j"$(nproc)"; \
    make install; \
    echo /usr/local/lib > /etc/ld.so.conf.d/scws.conf; \
    ldconfig; \
    # zhparser：PostgreSQL 中文分词解析器
    cd /tmp; \
    wget -q -T 60 https://github.com/amutu/zhparser/archive/refs/heads/master.tar.gz -O zhparser.tar.gz; \
    tar xzf zhparser.tar.gz; \
    cd zhparser-master; \
    SCWS_HOME=/usr/local make; \
    SCWS_HOME=/usr/local make install; \
    # 清理构建依赖与源码，保留运行时库与扩展文件
    cd /; \
    rm -rf /tmp/scws* /tmp/zhparser*; \
    apt-get purge -y build-essential postgresql-server-dev-16; \
    apt-get autoremove -y; \
    rm -rf /var/lib/apt/lists/*

# 健康检查与入口沿用基础镜像
