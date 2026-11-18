-- Run once after backing up an existing MySQL 8 gallery database.
-- This migration assumes the original picture table already has the base image columns.
ALTER TABLE picture
    ADD COLUMN fileHash varchar(64) NULL,
    ADD COLUMN perceptualHash varchar(16) NULL,
    ADD COLUMN picColor varchar(16) NULL,
    ADD COLUMN spaceId bigint NULL,
    ADD COLUMN reviewStatus int NOT NULL DEFAULT 0,
    ADD COLUMN reviewMessage varchar(512) NULL,
    ADD COLUMN reviewerId bigint NULL,
    ADD COLUMN reviewTime datetime NULL,
    ADD COLUMN shareCode varchar(64) NULL,
    ADD COLUMN shareExpireTime datetime NULL,
    ADD COLUMN viewCount bigint NOT NULL DEFAULT 0,
    ADD COLUMN downloadCount bigint NOT NULL DEFAULT 0;

CREATE INDEX idx_public_review ON picture(spaceId, reviewStatus, isDelete, createTime);
CREATE INDEX idx_picture_user ON picture(userId);
CREATE INDEX idx_picture_hash ON picture(fileHash);
CREATE INDEX idx_picture_perceptual ON picture(perceptualHash);
CREATE UNIQUE INDEX uk_picture_share ON picture(shareCode);

CREATE TABLE IF NOT EXISTS space (
    id bigint PRIMARY KEY,
    spaceName varchar(128) NOT NULL,
    spaceLevel int NOT NULL DEFAULT 0,
    spaceType int NOT NULL DEFAULT 0,
    userId bigint NOT NULL,
    maxSize bigint NOT NULL,
    maxCount bigint NOT NULL,
    totalSize bigint NOT NULL DEFAULT 0,
    totalCount bigint NOT NULL DEFAULT 0,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    INDEX idx_space_owner(userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS space_user (
    id bigint PRIMARY KEY,
    spaceId bigint NOT NULL,
    userId bigint NOT NULL,
    spaceRole varchar(16) NOT NULL DEFAULT 'viewer',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_space_user(spaceId,userId),
    INDEX idx_space_member(userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
