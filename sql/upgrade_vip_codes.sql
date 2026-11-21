-- Run once for an existing database after backing it up.
CREATE TABLE IF NOT EXISTS vip_code (
    codeHash CHAR(64) PRIMARY KEY,
    durationDays INT NOT NULL,
    createdBy BIGINT NOT NULL,
    createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    redeemedBy BIGINT NULL,
    redeemedAt DATETIME NULL,
    INDEX idx_vip_code_issuer (createdBy, createTime),
    INDEX idx_vip_code_redeemer (redeemedBy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
