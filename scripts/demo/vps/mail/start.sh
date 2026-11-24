#!/bin/sh
set -eu
umask 077

install -o opendkim -g opendkim -m 0400 /run/picus-mail/dkim-private.pem /etc/opendkim/picus.private
printf '127.0.0.1\n172.30.194.0/28\n' > /etc/opendkim/picus-internal
chmod 0644 /etc/opendkim/picus-internal
printf 'picus.test OK\n' > /etc/postfix/picus-recipients
awk '
    /^[[:space:]]*($|#)/ { next }
    /^[a-zA-Z0-9_.+%-]+@[a-zA-Z0-9.-]+[.][a-zA-Z0-9-]+$/ {
        print tolower($0) " OK"
        next
    }
    { print "Indirizzo non valido nella lista destinatari" > "/dev/stderr"; exit 1 }
' /run/picus-mail/destinatari.txt >> /etc/postfix/picus-recipients
postmap lmdb:/etc/postfix/picus-recipients
printf 'postmaster: picusmail\nnoreply: picusmail\nroot: picusmail\n' > /etc/postfix/aliases
postalias lmdb:/etc/postfix/aliases
chgrp postfix /etc/postfix/picus-recipients /etc/postfix/picus-recipients.lmdb \
    /etc/postfix/aliases /etc/postfix/aliases.lmdb
chmod 0640 /etc/postfix/picus-recipients /etc/postfix/picus-recipients.lmdb \
    /etc/postfix/aliases /etc/postfix/aliases.lmdb
install -d -o picusmail -g picusmail -m 0700 /var/lib/picus-capture \
    /var/lib/picus-capture/Maildir /var/lib/picus-capture/Maildir/new \
    /var/lib/picus-capture/Maildir/cur /var/lib/picus-capture/Maildir/tmp
domain=mail.picus.strawberrypenguin.it
login=picus-otp
test -s /run/picus-mail/otp-smtp.password
saslpasswd2 -p -c -f /etc/sasldb2 -u "$domain" "$login" < /run/picus-mail/otp-smtp.password
chown postfix:postfix /etc/sasldb2
chmod 0600 /etc/sasldb2
printf 'noreply@%s %s@%s\n' "$domain" "$login" "$domain" > /etc/postfix/picus-otp-senders
postmap lmdb:/etc/postfix/picus-otp-senders
chgrp postfix /etc/postfix/picus-otp-senders.lmdb
chmod 0640 /etc/postfix/picus-otp-senders.lmdb
postconf -M '2525/inet=2525 inet n - n - - smtpd'
postconf -P \
    '2525/inet/syslog_name=postfix/otp' \
    '2525/inet/cyrus_sasl_config_path=/etc/postfix/sasl' \
    '2525/inet/smtpd_sasl_auth_enable=yes' \
    '2525/inet/smtpd_relay_restrictions=permit_sasl_authenticated,reject' \
    '2525/inet/smtpd_recipient_restrictions=permit_sasl_authenticated,reject' \
    '2525/inet/smtpd_sender_login_maps=lmdb:/etc/postfix/picus-otp-senders' \
    '2525/inet/smtpd_sender_restrictions=reject_authenticated_sender_login_mismatch' \
    '2525/inet/smtpd_client_message_rate_limit=10'

postfix check
opendkim -x /etc/opendkim/picus.conf
exec postfix start-fg
