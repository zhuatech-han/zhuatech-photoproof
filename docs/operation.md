# PhotoProof 操作手册 / Operation guide

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。
ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · https://www.zhuatech.cn/ · han@zhuatech.cn / jack@zhuatech.cn · WhatsApp +86 17521234993.

## 初次使用 / First use

1. 在私有.env读初始账号。后台添加真实工作室、客户及需要的员工，客户登录账号选择Client角色并绑定对应客户。账号固定客户身份，不能把既有员工账号改成客户账号。 / Read your private bootstrap credentials. Add studios, clients and staff; create a Client-role account linked to the correct client. Linked identities are immutable.
2. 建项目，选择客户、摄影师、修图人员和套餐。摄影师须有upload权限、修图人员须有edit权限，均在客户工作室。至少选片≤含片≤最多选片≤100。 / Create a project with the client, eligible studio workers and package limits.
3. 草稿上传JPEG/PNG真实样片，同名活动样片拒绝；草稿可移除，发布后锁定套餐与样片。 / Upload real proofs to the draft. Active filenames are unique. Publication freezes proofs and the package.
4. 客户登录或用分享链接加访问码打开画廊，选择照片、填写备注、查看并确认费用。员工无法替客户提交。 / Clients select, leave notes and acknowledge the exact quote through their own account or share.
5. 修图台取样片原文件，在外部软件修图，为每张选中照片上传版本。送客户审阅后，客户认可或说明修改，旧版本保留。已获认可的文件不能暗中替换。 / Download sources, retouch externally, upload finals and submit for client review. Approved files cannot be silently replaced.
6. 收款人员核对真实外部交易后登记收款。全部最新文件获客户认可且净收款足额后，拥有release权限的员工开放交付。客户下载单张或ZIP并确认收讫归档。 / Record verified receipts. Staff release delivery only after full payment and approval; the client downloads and closes.

## 分享与期限 / Sharing and expiry

分享链接包含片段令牌，不使用查询参数；8位访问码单独给客户。凭据只显示一次，遗失可撤销并生成新链接。撤销和过期即时使分享会话失效；客户停用或项目取消也会拒绝访问。普通客户账号可见自己所有已发布项目，分享会话只能见一个项目。 / Share links use URL fragments and an eight-digit PIN. Credentials appear once. Revocation/expiry is immediate. Account access and single-project shares have different scopes.

二维码必须指向客户可访问的部署地址。扫码只是打开画廊，不代表付款、客户认可或已下载。 / A QR opens a reachable gallery; scanning is not payment, approval or download proof.

## 修改、退款与冲销 / Changes, refunds and reversals

交付前重新选片需要原因，保留旧轮次。若加选已收款使净收款高于基础价，先实际退款并登记，再重开。退款引用具体收款，只能退剩余金额；冲销用于错误登记，金额须与原记录相同，不能重复冲销或绕过退款引用链。 / Reopening retains history and requires refunds first when receipts exceed the base price. Refunds reference their receipt; reversals correct errors rather than transfer money.

交付后退款降低净收款时，新下载入口会锁定，但无法撤回过去下载。取消项不能继续收款，可以登记真实退款。 / A post-delivery refund may lock future downloads but cannot recall prior files. Cancelled jobs accept actual refunds, not new receipts.

## 常见问题 / Troubleshooting

- 数量不足或超限：核对套餐；已冻结需摄影师重新开放。 / Check package limits; frozen selections need staff reopening.
- 送审失败：每张选中照片需要当前轮次修图文件。 / Every pick needs a final in the current round.
- 交付失败：核对最新版本全部认可、实际登记足额、期限未过。 / Check approvals, net receipts and gallery expiry.
- 文件格式失败：必须真实JPEG/PNG且扩展名一致；10MB、2400万像素、10000单边限制。 / Match format and extension and respect size/pixel limits.
- 版本冲突：刷新核对；网络中断显示结果未知时先检查，不自动重复提交。上传批次显示逐张结果，未知文件不要盲目再传。 / Refresh on version conflicts. Check unknown writes before trying again; uploads show per-file outcomes.
- 无权限：检查账号启用、工作室、岗位和指派；更换账号不会继承上一个人的画廊。 / Check status, studio, role and assignment. Identity changes clear private UI state.
- 账号修改后掉线：密码重置、停用或工作室停用即时生效，重启也会使内存会话失效，数据仍在。 / Reset/disable or service restart invalidates sessions; persistent business data remains.
