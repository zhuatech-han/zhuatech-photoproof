# PhotoProof API与权限 / API and access

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。
ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · https://www.zhuatech.cn/ · han@zhuatech.cn / jack@zhuatech.cn · WhatsApp +86 17521234993.

所有入口同源/api，服务端会话鉴权。先GET/auth/csrf读取header与token，写请求在该header附token；401需重新登录，403权限不足，409业务/版本冲突，413体积限制，429限流，507存储失败。错误仅返回code，不输出私有路径。 / Use session authentication and the actual CSRF header returned by the server.

| 接口 / Endpoint | 业务 / Behavior |
| --- | --- |
| /auth/login, /me, /password, /logout | 登录、当前身份、自改密码、退出 / Sign-in, identity, password and sign-out |
| GET /jobs?mode=jobs/edit/cash/portal | 根据真实岗位/客户范围列表 / Role-scoped project list |
| POST /jobs; PUT /jobs/{id} | 新增与草稿修改 / Create and draft edit |
| POST /jobs/{id}/actions/{action} | publish/submit/review/release/close/reopen/extend/cancel |
| PUT /jobs/{id}/selection | 客户逐片选择和备注 / Client picks and notes |
| POST /jobs/{id}/reviews | 客户认可/要求修改当前文件 / Exact-version client review |
| POST /jobs/{id}/media | multipart实际上传；version/kind/photoId / Real proof/final upload |
| GET /media/{id}/thumb,preview,original | 每次实时鉴权；原片受交付控制 / Authorized private files |
| GET /jobs/{id}/download | 当前交付ZIP / Current approved delivery ZIP |
| POST /jobs/{id}/cash | RECEIPT/REFUND/REVERSAL外部事实 / External transaction records |
| /clients; /clients/import | 客户资料及原子批量导入 / Client master and atomic import |
| /admin/{users,roles,permissions,menus,departments,dictionaries,settings} | 对应管理权限且ALL范围 / Scoped administration |
| GET /admin-options | 账号管理安全选项 / Safe account-management options |
| /jobs/{id}/shares; /share/exchange | 生成/撤销授权、POST凭据交换 / Create/revoke share and credential exchange |
| /reports; /audit | 按币种报告及范围记录 / Currency-separated reports and audit |

项目写操作必须提交version；账号、工作室、角色修改也验证其version。新项目先设客户、摄影师、修图人员、日期、分类与套餐；上传完成返回最新详情，连续上传使用新version。 / Mutations need the latest version. Serial uploads use the returned version.

ALL/DEPARTMENT/ASSIGNED/CLIENT分别指全部工作室/所在工作室/创建或指派的项目/绑定客户项目。后台管理只允许ALL范围；临时分享只能见授权项目。ASSIGNED角色的审计只显示本人动作；项目事件按项目范围可读。 / Admin writes require ALL; assigned audit is personal and project history is scoped.
