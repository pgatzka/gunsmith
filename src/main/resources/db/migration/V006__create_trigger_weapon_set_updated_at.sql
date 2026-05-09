create trigger weapon_set_updated_at
    before insert or update
    on weapon
    for each row
execute function set_updated_at();