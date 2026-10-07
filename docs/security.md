# PhotoProof 安全边界 / Security boundaries

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。
ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · https://www.zhuatech.cn/ · han@zhuatech.cn / jack@zhuatech.cn · WhatsApp +86 17521234993.

密码BCrypt12、12–72字节且含大小写数字；服务端会话HttpOnly/Strict，HTTPS时Secure。写操作带CSRF；登录失败按IP/账号限流8次/5分钟，分享按IP/令牌散列限流。限制为单实例内存，不替代边缘安全或MFA。 / BCrypt12, strong byte-limited passwords, session cookies, CSRF and bounded single-instance throttling.

权限每次读数据库，禁用和重置旧密码即时失效；至少保留一个启用工作室内、ALL范围且具全部管理权限的管理员。客户身份固定绑定；员工不能调用客户确认；分享只授权一个项目，数据库仅留令牌/PIN散列。 / Live scopes and password fingerprints invalidate stale access and preserve an administrator with recovery permissions.

照片格式、扩展名、字节、像素、容量与状态逐项验证；随机UUID路径，禁止客户端路径与符号链接。水印预览重新编码；原文件下载SHA256核验，私有no-store缓存。客户交付前不能取原片，当前交付不能混入旧轮次版本。水印无法防截屏；原文件EXIF可能含定位信息，上传前由文件所有者检查。 / Validate real media, use private generated paths, re-encode previews and verify originals. Watermarks do not prevent screenshots; original EXIF remains the owner's responsibility.

金额使用BigDecimal，不四舍五入吞掉精度；写入检查版本及锁，资金与业务审计同事务。历史文件、选择、审批、款项不可覆盖。客户CSV导出对隐藏前缀公式加保护，原子导入全部成功才提交。 / Exact decimals, version checks, serial writes and immutable history; guarded CSV and atomic import.

不是跨公司SaaS、法定签名、银行卡支付平台或不可篡改外部审计系统。数据库/系统管理员能接触文件和库，经营主体必须管理其权限、备份、留存和法律依据。安全问题使用官网或以上联系人私下反馈。 / No tenant-isolated SaaS, certified signatures, payment processing or externally immutable audit. Operators must govern privileged access and retention.
