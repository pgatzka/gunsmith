create trigger category_set_updated_at
    before insert or update
    on category
    for each row
execute function set_updated_at();