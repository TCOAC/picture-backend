create table if not exists user
(
    id            bigint auto_increment comment 'id'
        primary key,
    userAccount   varchar(256)                       not null comment '账号',
    userPassword  varchar(512)                       not null comment '密码',
    userName      varchar(256)                       null comment '用户昵称',
    userAvatar    varchar(1024)                      null comment '用户头像',
    userProfile   varchar(512)                       null comment '用户简介',
    userRole      varchar(256) default 'user'        not null comment '用户角色：user/admin/vip',
    editTime      datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除',
    vipExpireTime datetime                           null comment '会员过期时间',
    vipCode       varchar(128)                       null comment '会员兑换码',
    vipNumber     bigint                             null comment '会员编号',
    shareCode     varchar(256)                       null comment '分享码',
    inviteUser    bigint                             null comment '邀请用户 id',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

create table if not exists picture
(
    id bigint primary key,
    url varchar(2048) not null,
    name varchar(128) not null,
    introduction varchar(800),
    category varchar(64),
    tags varchar(1024) default '[]',
    picSize bigint not null,
    picWidth int not null,
    picHeight int not null,
    picScale double,
    picFormat varchar(16),
    fileHash varchar(64),
    perceptualHash varchar(16),
    picColor varchar(16),
    spaceId bigint,
    userId bigint not null,
    reviewStatus int not null default 0,
    reviewMessage varchar(512),
    reviewerId bigint,
    reviewTime datetime,
    shareCode varchar(64),
    shareExpireTime datetime,
    viewCount bigint not null default 0,
    downloadCount bigint not null default 0,
    createTime datetime not null default CURRENT_TIMESTAMP,
    editTime datetime not null default CURRENT_TIMESTAMP,
    updateTime datetime not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
    isDelete tinyint not null default 0,
    index idx_public_review (spaceId,reviewStatus,isDelete,createTime),
    index idx_user (userId),
    index idx_hash (fileHash),
    index idx_perceptual (perceptualHash),
    unique key uk_share (shareCode)
) collate = utf8mb4_unicode_ci;

create table if not exists space
(
    id bigint primary key,
    spaceName varchar(128) not null,
    spaceLevel int not null default 0,
    spaceType int not null default 0,
    userId bigint not null,
    maxSize bigint not null,
    maxCount bigint not null,
    totalSize bigint not null default 0,
    totalCount bigint not null default 0,
    createTime datetime not null default CURRENT_TIMESTAMP,
    updateTime datetime not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
    isDelete tinyint not null default 0,
    index idx_space_owner (userId)
) collate = utf8mb4_unicode_ci;

create table if not exists space_user
(
    id bigint primary key,
    spaceId bigint not null,
    userId bigint not null,
    spaceRole varchar(16) not null default 'viewer',
    createTime datetime not null default CURRENT_TIMESTAMP,
    unique key uk_space_user (spaceId,userId),
    index idx_member (userId)
) collate = utf8mb4_unicode_ci;
