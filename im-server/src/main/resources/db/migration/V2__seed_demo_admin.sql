INSERT INTO enterprises(id, name, code)
SELECT 'ent_demo', '演示企业', 'DEMO'
WHERE NOT EXISTS (SELECT 1 FROM enterprises WHERE id = 'ent_demo');

INSERT INTO departments(id, enterprise_id, parent_id, name, sort_order)
SELECT 'dept_demo_root', 'ent_demo', NULL, '默认部门', 0
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE id = 'dept_demo_root');

INSERT INTO users(id, enterprise_id, phone, email, display_name, status)
SELECT 'u_admin_demo', 'ent_demo', '18800000000', 'admin@example.com', '系统管理员', 'active'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'u_admin_demo' OR phone = '18800000000');

INSERT INTO admin_users(id, user_id, role_id, enabled)
SELECT 'admin_demo_super', id, 'role_super_admin', TRUE
FROM users
WHERE phone = '18800000000'
  AND NOT EXISTS (SELECT 1 FROM admin_users WHERE id = 'admin_demo_super');

INSERT INTO department_members(department_id, user_id, position_name)
SELECT 'dept_demo_root', id, '系统管理员'
FROM users
WHERE phone = '18800000000'
  AND NOT EXISTS (SELECT 1 FROM department_members WHERE department_id = 'dept_demo_root' AND user_id = (SELECT id FROM users WHERE phone = '18800000000'));
