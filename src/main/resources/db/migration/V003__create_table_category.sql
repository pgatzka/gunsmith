create table category
(
    id         integer                  not null generated always as identity primary key,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    tarkov_id  varchar(255)             not null unique,
    name       varchar(255)             not null
);