create table weapon
(
    id                integer                  not null generated always as identity primary key,
    created_at        timestamp with time zone not null default now(),
    updated_at        timestamp with time zone not null default now(),
    tarkov_id         varchar(255)             not null unique,
    name              varchar(255)             not null,
    icon_link         varchar(255)             not null,
    category_id        integer      not null references category(id),
    ergonomics        double precision         not null,
    recoil_horizontal integer                  not null,
    recoil_vertical   integer                  not null
);
