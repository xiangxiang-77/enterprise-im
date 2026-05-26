CREATE TABLE IF NOT EXISTS app_templates (
    id VARCHAR(64) PRIMARY KEY,
    template_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    icon VARCHAR(128),
    config_json CLOB
);

INSERT INTO app_templates(id, template_id, name, description, icon, config_json) VALUES
    ('tmpl_jira', 'jira', 'Jira', '项目管理工具', 'jira', '{"url":"https://your-domain.atlassian.net/jira","visibleDepartmentId":null,"sortOrder":0,"enabled":true}'),
    ('tmpl_confluence', 'confluence', 'Confluence', '知识库管理', 'confluence', '{"url":"https://your-domain.atlassian.net/wiki","visibleDepartmentId":null,"sortOrder":0,"enabled":true}'),
    ('tmpl_gitlab', 'gitlab', 'GitLab', '代码仓库', 'gitlab', '{"url":"https://gitlab.your-domain.com","visibleDepartmentId":null,"sortOrder":0,"enabled":true}'),
    ('tmpl_figma', 'figma', 'Figma', '设计协作', 'figma', '{"url":"https://www.figma.com","visibleDepartmentId":null,"sortOrder":0,"enabled":true}'),
    ('tmpl_notion', 'notion', 'Notion', '笔记与文档', 'notion', '{"url":"https://www.notion.so","visibleDepartmentId":null,"sortOrder":0,"enabled":true}'),
    ('tmpl_slack', 'slack', 'Slack', '团队通讯', 'slack', '{"url":"https://slack.com","visibleDepartmentId":null,"sortOrder":0,"enabled":true}');
