-- Nullable for orders created before snapshots were introduced.
-- Their names are filled on the first read while the menu item still exists.
ALTER TABLE order_items ADD COLUMN menu_item_name VARCHAR(50);
