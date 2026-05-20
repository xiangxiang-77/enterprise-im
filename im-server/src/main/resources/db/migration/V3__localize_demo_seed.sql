UPDATE admin_roles
SET description = CASE id
    WHEN 'role_super_admin' THEN '全部权限'
    WHEN 'role_operator' THEN '组织与运营管理'
    WHEN 'role_auditor' THEN '消息审计与风险控制'
    WHEN 'role_readonly_ops' THEN '只读运维'
    ELSE description
END
WHERE id IN ('role_super_admin', 'role_operator', 'role_auditor', 'role_readonly_ops');

UPDATE users
SET display_name = '系统管理员'
WHERE id = 'u_admin_demo'
  AND phone = '18800000000'
  AND display_name = 'Demo Admin';
