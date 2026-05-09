create table weapon_slot
(
    created_at timestamp with time zone not null default now(),
    weapon_id  integer                  not null references weapon (id) on delete cascade,
    slot_id    integer                  not null references slot (id)
);