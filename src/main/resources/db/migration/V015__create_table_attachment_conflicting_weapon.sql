create table attachment_conflicting_weapon
(
    created_at            timestamp with time zone not null default now(),
    attachment_id         integer                  not null references attachment (id),
    conflicting_weapon_id integer                  not null references weapon (id)
);

