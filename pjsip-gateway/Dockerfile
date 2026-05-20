FROM debian:bookworm-slim AS pjsip-builder

ARG PJSIP_VERSION=2.14

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        wget \
        build-essential \
        autoconf \
        libasound2-dev \
    && rm -rf /var/lib/apt/lists/*

RUN wget -O /tmp/pjproject.tar.gz "https://github.com/pjsip/pjproject/archive/refs/tags/${PJSIP_VERSION}.tar.gz" \
    && mkdir -p /tmp/pjproject \
    && tar -xzf /tmp/pjproject.tar.gz -C /tmp/pjproject --strip-components=1 \
    && cd /tmp/pjproject \
    && ./configure --prefix=/opt/pjsip --disable-video --disable-opencore-amr \
    && make dep \
    && make -j"$(nproc)" \
    && make install \
    && mkdir -p /opt/pjsip/bin \
    && find /tmp/pjproject/pjsip-apps/bin -type f -name 'pjsua-*' -exec cp {} /opt/pjsip/bin/pjsua \; \
    && chmod +x /opt/pjsip/bin/pjsua \
    && /opt/pjsip/bin/pjsua --version

FROM python:3.12-slim

WORKDIR /app
COPY app.py /app/app.py
COPY --from=pjsip-builder /opt/pjsip /opt/pjsip

RUN apt-get update \
    && apt-get install -y --no-install-recommends libasound2 \
    && rm -rf /var/lib/apt/lists/*

ENV PJSIP_GATEWAY_PORT=7070
ENV PJSUA_BIN=/opt/pjsip/bin/pjsua
ENV PATH=/opt/pjsip/bin:$PATH
EXPOSE 7070

CMD ["python", "/app/app.py"]
