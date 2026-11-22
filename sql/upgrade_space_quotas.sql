-- Apply after backing up the database. Only rows with the previous default quota are changed.
UPDATE space
SET maxSize = 1000 * 1024 * 1024
WHERE spaceLevel = 1
  AND maxSize = 1024 * 1024 * 1024
  AND isDelete = 0;

UPDATE space
SET maxSize = 10000 * 1024 * 1024
WHERE spaceLevel = 2
  AND maxSize = 10240 * 1024 * 1024
  AND isDelete = 0;
