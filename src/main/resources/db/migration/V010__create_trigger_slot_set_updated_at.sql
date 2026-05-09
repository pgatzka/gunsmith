create trigger slot_set_updated_at
    before insert or update
    on slot
    for each row
execute function set_updated_at();