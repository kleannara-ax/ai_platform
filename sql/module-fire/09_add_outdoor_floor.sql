USE fireweb;

INSERT INTO floor (floor_id, floor_name, sort_order)
VALUES (7, '옥외', 60)
ON DUPLICATE KEY UPDATE floor_name = VALUES(floor_name), sort_order = VALUES(sort_order);
