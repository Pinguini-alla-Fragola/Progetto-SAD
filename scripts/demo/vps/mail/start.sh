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
postfix check
opendkim -x /etc/opendkim/picus.conf
exec postfix start-fg
