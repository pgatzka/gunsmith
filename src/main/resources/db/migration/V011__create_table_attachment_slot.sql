create table attachment_slot
(
    created_at    timestamp with time zone not null default now(),
    attachment_id integer                  not null references attachment (id) on delete cascade,
    slot_id       integer                  not null references slot (id)
);
