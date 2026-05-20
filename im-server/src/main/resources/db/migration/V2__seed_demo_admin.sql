INSERT INTO users(id, phone, email, display_name, status)
SELECT 'u_admin_demo', '18800000000', 'admin@example.com', 'Demo Admin', 'active'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'u_admin_demo' OR phone = '18800000000');

INSERT INTO admin_users(id, user_id, role_id, enabled)
SELECT 'admin_demo_super', id, 'role_super_admin', TRUE
FROM users
WHERE phone = '18800000000'
  AND NOT EXISTS (SELECT 1 FROM admin_users WHERE id = 'admin_demo_super');
