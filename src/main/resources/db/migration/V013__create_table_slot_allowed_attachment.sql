create table slot_allowed_attachment
(
    created_at    timestamp with time zone not null default now(),
    slot_id               integer not null references slot (id),
    allowed_attachment_id integer not null references attachment (id)
);