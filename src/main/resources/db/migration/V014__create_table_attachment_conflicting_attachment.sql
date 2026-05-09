create table attachment_conflicting_attachment
(
    created_at                timestamp with time zone not null default now(),
    attachment_id             integer                  not null references attachment (id),
    conflicting_attachment_id integer                  not null references attachment (id)
);