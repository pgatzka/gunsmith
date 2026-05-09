create table build
(
    id                integer                  not null generated always as identity primary key,
    created_at        timestamp with time zone not null default now(),
    weapon_id         integer                  not null references weapon (id) on delete cascade,
    ergonomics        double precision         not null,
    recoil_vertical   double precision         not null,
    recoil_horizontal double precision         not null,
    json              jsonb                    not null,
    json_hash         varchar(32)              not null unique
);