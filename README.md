# 云图库后端

Spring Boot 2.7 / Java 8 字节码，MyBatis-Plus 与 MySQL。公共图片经过审核后展示；空间图片按成员角色授权。文件可保存至本地磁盘或腾讯云 COS。

## 已实现

- 注册、登录、登出和管理员用户管理；新密码使用 BCrypt，旧版加盐 MD5 密码在成功登录时自动升级。
- 公共图库分页与属性筛选、审核队列、所有者编辑与删除、URL 导入与管理员批量网页采集。
- 公共图库使用 Caffeine 本地缓存。启用 Redis 后提供第二级缓存，并在图片状态变更时同步缓存版本。
- 私有/团队空间、额度、成员角色、配额原子预留，以及分类、标签、尺寸、用户和空间容量分析。
- 私有媒体读取授权、7 天分享链接和撤销、下载计数。
- 图片校验、内容 SHA-256 去重存储、缩略图、主色与感知哈希索引、颜色/相似检索、旋转/翻转。
- 团队图片 WebSocket 协作锁与编辑通知；AI 图像编辑对接 OpenAI Images Edits 兼容接口。
- 公网 URL 下载启用 DNS 公网地址校验、关闭重定向并限制文件尺寸。

## 本地运行

需要 JDK 21（源码按 Java 8 字节码编译）、Maven 3.8+、MySQL 8。先在 MySQL 执行 [`sql/create_table.sql`](sql/create_table.sql)，然后设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`APP_STORAGE=local`，再运行：

```powershell
mvn spring-boot:run
```

接口地址为 `http://localhost:8123/api`。上传文件写到 `./data/uploads`；媒体 URL 仍经过应用鉴权。前端开发服务器通过 Vite 将 `/api` 与 `/api/ws` 代理到该地址。首次将用户升级为站点管理员时，先正常注册，再由数据库管理员执行 `UPDATE user SET userRole='admin' WHERE userAccount='your-account';`。

COS 模式需设置 `APP_STORAGE=cos` 以及 `COS_HOST`、`COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_REGION`、`COS_BUCKET`。AI 图片编辑需要设置 `AI_IMAGE_API_URL`、`AI_IMAGE_API_KEY`、`AI_IMAGE_MODEL`；模型服务不配置时此接口明确返回不可用。

本地运行默认使用进程内会话和 Caffeine 缓存。需要共享会话与二级缓存时，启动 Redis 并设置 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`SESSION_STORE_TYPE=redis`、`APP_CACHE_REDIS_ENABLED=true`。Docker Compose 已包含 Redis 服务，需在 `.env` 中分别设置数据库和 Redis 密码。

## 数据库升级

新库执行 `sql/create_table.sql`。已有图库需备份数据库后，按 [`sql/upgrade_existing_picture.sql`](sql/upgrade_existing_picture.sql) 为原图片表补列并创建空间表。新表脚本不会自动修改已有表。

## 验证

```powershell
mvn test
mvn package
```

测试使用隔离的 H2 MySQL 模式数据库，验证注册登录、上传审核、空间权限、分享撤销、配额回滚，以及公共标签接口的隐私隔离。Docker Compose 启动说明见仓库根目录部署文件，前端目录需要与后端目录处于同一父目录。

协作锁当前存储在单个应用实例内存中，适合单节点部署；多副本部署前需把会话和协作锁迁移到 Redis，并为图片变换/协作事件配置共享协调层。大规模冷存储清理和分片也需按目标云环境配置。
