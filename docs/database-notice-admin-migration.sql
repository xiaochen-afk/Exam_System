-- MySQL 8.0.44. Run once on the existing schema before deploying the new server.
-- Existing records retain published_at. Future scheduled records remain hidden until that time.
-- Successful ALTER statements auto-commit. Stop on error and inspect the completed step before resuming.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE notice
    ADD COLUMN status ENUM('DRAFT', 'PUBLISHED', 'WITHDRAWN') NULL AFTER published_at,
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN withdrawn_at DATETIME(6) NULL,
    ADD KEY ix_notice_admin_status_updated (status, updated_at DESC, id DESC);

START TRANSACTION;
UPDATE notice
SET status = CASE WHEN published_at IS NULL THEN 'DRAFT' ELSE 'PUBLISHED' END
WHERE status IS NULL;

INSERT INTO auth_permission (code, name) VALUES
    ('notices.read', '查看后台资讯'),
    ('notices.write', '新增和编辑资讯'),
    ('notices.attachments.write', '上传和移除资讯附件'),
    ('notices.publish', '发布和下架资讯');

INSERT INTO auth_role_permission (role_code, scope_type, permission_code) VALUES
    ('ORGANIZER', 'CATALOG', 'notices.read'),
    ('ORGANIZER', 'CATALOG', 'notices.write'),
    ('ORGANIZER', 'CATALOG', 'notices.attachments.write'),
    ('ORGANIZER', 'CATALOG', 'notices.publish');
COMMIT;

ALTER TABLE notice MODIFY COLUMN status
    ENUM('DRAFT', 'PUBLISHED', 'WITHDRAWN') NOT NULL DEFAULT 'DRAFT';
