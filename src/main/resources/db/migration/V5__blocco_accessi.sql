alter table utenze_account
    add column tentativi_falliti integer not null default 0;
alter table utenze_account
    add column bloccato_fino timestamp with time zone;
