# Skill: 多智能体协作

## 目标

把大项目拆给多个 agent/开发者并行推进，减少冲突。

## 角色

- 产品 agent：需求拆解、验收标准、优先级。
- 架构 agent：总体设计、协议、DB、模块边界。
- 服务端 agent：Java、DB、TCP、Docker。
- Web agent：当前 `im-ui`、后台管理。
- Desktop agent：Qt 5.9.3。
- Mobile agent：Flutter。
- QA agent：测试计划、自动化、冒烟验收。
- DevOps agent：Docker、环境、CI/CD、部署脚本。

## 协作规则

- 所有人先读 `codex.md`。
- 每个 agent 只改自己负责目录。
- 跨端契约只通过文档和 schema 改，不口头同步。
- API、TCP 协议、DB schema 改动必须先更新设计文档。
- 每日更新 `codex.md` 进度。

## 推荐并行切分

- 服务端先建协议、DB、auth、socket。
- Web 修乱码并对接真实 auth/socket。
- 后台从组织架构和用户管理开始。
- Qt/Flutter 先接登录和单聊文本。
- QA 同步写协议测试和冒烟清单。

## 完成标准

- 并行开发无大量冲突。
- 任一端可通过文档独立接协议。
- 每天能看到真实可运行增量。

