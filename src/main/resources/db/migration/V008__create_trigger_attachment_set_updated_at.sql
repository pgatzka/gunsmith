create trigger attachment_set_updated_at
    before insert or update
    on attachment
    for each row
execute function set_updated_at();