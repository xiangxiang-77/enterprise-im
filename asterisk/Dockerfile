FROM alpine:3.20

RUN apk add --no-cache asterisk asterisk-sample-config

EXPOSE 5060/tcp 5060/udp 10000-10020/udp

CMD ["asterisk", "-f", "-U", "root"]
