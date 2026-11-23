FROM alpine:3.22
RUN apk add --no-cache postfix opendkim ca-certificates \
    && addgroup -S picusmail \
    && adduser -S -D -H -h /var/lib/picus-capture -s /sbin/nologin -G picusmail picusmail \
    && postconf -M 'smtp/inet=smtp inet n - n - - smtpd'
COPY main.cf /etc/postfix/main.cf
COPY aliases.regexp /etc/postfix/picus-aliases
COPY opendkim.conf /etc/opendkim/picus.conf
COPY start.sh /usr/local/bin/picus-smtp
RUN chmod 0755 /usr/local/bin/picus-smtp
ENTRYPOINT ["/usr/local/bin/picus-smtp"]
