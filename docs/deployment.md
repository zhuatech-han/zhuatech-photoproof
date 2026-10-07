# PhotoProof 部署与一致恢复 / Deployment and consistent restore

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。
ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · https://www.zhuatech.cn/ · han@zhuatech.cn / jack@zhuatech.cn · WhatsApp +86 17521234993.

运行Compose的MySQL8.4、Java21后端与非root Nginx。数据库仅容器内网络访问，媒体卷无公开静态路径；后端使用非root用户。上线使用可信域名/HTTPS反向代理，COOKIE_SECURE=true，定期更新受支持依赖并自行验收设备和容量。 / Run the Compose services with private MySQL/media, a non-root backend and Nginx. Use trusted HTTPS and verify your deployment.

备份脚本针对指定Compose项目：停止后台写入 → 数据库单事务dump → 同一时点照片tar → SHA256清单 → 私有ZIP → 恢复后台。会话为内存数据，服务重启须重新登录。请确保没有绕过后台直接修改数据库或媒体的其他写进程。 / Backup stops application writes, captures both stores and hashes, then restarts. Do not allow out-of-band writes during capture.

恢复只允许全新项目，不覆盖已有容器或数据卷。先校验ZIP成员/散列以及tar目录、UUID和普通文件属性，再构建镜像、初始化独立数据库口令、导入SQL、恢复媒体、启动服务。原管理员的密码保留为数据库散列，恢复.env的ADMIN_PASSWORD不改写已有散列。 / Restore refuses existing resources and checks archives before starting. Restored account hashes are preserved; bootstrap settings do not reset existing passwords.

私有备份、.env、quality-state、客户图片和导出不进入Git。磁盘/数据库必须一起备份；只有数据库备份无法恢复照片。大数据恢复需自行评估脚本内存和磁盘，当前脚本在读取归档时使用内存，不适合超大图库。 / Keep backups and credentials outside Git. Restore is memory-based and requires capacity assessment for large galleries.

API反代保留正确Host/HTTPS协议，限制请求大小11MB，禁止给/api/media配置公开缓存。日志不应记录JSON登录正文、访问码、令牌或客户图片。 / Keep API/media private, preserve proxy headers and never log credentials or gallery tokens.
