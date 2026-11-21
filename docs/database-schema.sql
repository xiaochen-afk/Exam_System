-- MySQL 8.0.44 schema draft. Select a dedicated empty schema before running it.
-- Application writes UTC DATETIME(6) values. No account, secret, or business data is seeded here.
SET SESSION time_zone = '+00:00';
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE school (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_school_code UNIQUE (code),
    KEY ix_school_status_name (status, name, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_kind ENUM('CANDIDATE', 'STAFF') NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    session_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_user_id_kind UNIQUE (id, account_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE web_credential (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    identifier VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    identifier_normalized VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (LOWER(TRIM(identifier))) STORED,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_changed_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_web_identifier UNIQUE (identifier_normalized),
    CONSTRAINT fk_web_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidate_profile (
    candidate_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    account_kind ENUM('CANDIDATE', 'STAFF') NOT NULL DEFAULT 'CANDIDATE',
    candidate_type ENUM('STUDENT', 'SOCIAL') NULL,
    school_id BIGINT UNSIGNED NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_profile_kind CHECK (account_kind = 'CANDIDATE'),
    CONSTRAINT ck_profile_school CHECK (
        (candidate_type IS NULL AND school_id IS NULL)
        OR (candidate_type IS NOT NULL AND (
            (candidate_type = 'STUDENT' AND school_id IS NOT NULL)
            OR (candidate_type = 'SOCIAL' AND school_id IS NULL)
        ))
    ),
    CONSTRAINT fk_profile_candidate FOREIGN KEY (candidate_id, account_kind)
        REFERENCES app_user (id, account_kind),
    CONSTRAINT fk_profile_school FOREIGN KEY (school_id) REFERENCES school (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wechat_identity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT UNSIGNED NOT NULL,
    app_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    open_id VARBINARY(128) NOT NULL,
    bound_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_wechat_openid UNIQUE (app_id, open_id),
    CONSTRAINT uq_wechat_candidate_app UNIQUE (candidate_id, app_id),
    CONSTRAINT fk_wechat_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mini_program_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wechat_identity_id BIGINT UNSIGNED NOT NULL,
    session_version BIGINT UNSIGNED NOT NULL,
    token_digest BINARY(32) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT uq_mini_token_digest UNIQUE (token_digest),
    CONSTRAINT ck_mini_session_time CHECK (issued_at < expires_at AND (revoked_at IS NULL OR issued_at <= revoked_at)),
    CONSTRAINT fk_mini_wechat FOREIGN KEY (wechat_identity_id) REFERENCES wechat_identity (id),
    KEY ix_mini_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_subject (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_subject_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_site (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(200) NOT NULL,
    qualification_status ENUM('UNQUALIFIED', 'QUALIFIED') NOT NULL DEFAULT 'UNQUALIFIED',
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_site_school FOREIGN KEY (school_id) REFERENCES school (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_room (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    capacity INT UNSIGNED NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_room_site_name UNIQUE (site_id, name),
    CONSTRAINT uq_room_id_site UNIQUE (id, site_id),
    CONSTRAINT ck_room_capacity CHECK (capacity > 0),
    CONSTRAINT fk_room_site FOREIGN KEY (site_id) REFERENCES exam_site (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One project is one exam offering for one subject, shared by alternate sessions.
CREATE TABLE exam_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_id BIGINT UNSIGNED NOT NULL,
    application_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    max_score DECIMAL(7,2) NOT NULL DEFAULT 100.00,
    CONSTRAINT uq_project_subject_code UNIQUE (subject_id, application_code),
    CONSTRAINT ck_project_score CHECK (max_score > 0),
    CONSTRAINT ck_project_code CHECK (application_code IS NULL OR (
        application_code <> '' AND application_code = UPPER(TRIM(application_code))
    )),
    CONSTRAINT fk_project_subject FOREIGN KEY (subject_id) REFERENCES exam_subject (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_project_site (
    project_id BIGINT UNSIGNED NOT NULL,
    site_id BIGINT UNSIGNED NOT NULL,
    registration_open TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id, site_id),
    CONSTRAINT ck_project_site_open CHECK (registration_open IN (0, 1)),
    CONSTRAINT fk_project_site_project FOREIGN KEY (project_id) REFERENCES exam_project (id),
    CONSTRAINT fk_project_site_site FOREIGN KEY (site_id) REFERENCES exam_site (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNSIGNED NOT NULL,
    site_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    registration_starts_at DATETIME(6) NOT NULL,
    registration_ends_at DATETIME(6) NOT NULL,
    exam_starts_at DATETIME(6) NOT NULL,
    exam_ends_at DATETIME(6) NOT NULL,
    capacity INT UNSIGNED NOT NULL,
    registered_count INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('DRAFT', 'PUBLISHED', 'COMPLETED') NOT NULL DEFAULT 'DRAFT',
    ticket_published_at DATETIME(6) NULL,
    ticket_published_by BIGINT UNSIGNED NULL,
    score_published_at DATETIME(6) NULL,
    score_published_by BIGINT UNSIGNED NULL,
    CONSTRAINT uq_session_id_project_site UNIQUE (id, project_id, site_id),
    CONSTRAINT ck_session_times CHECK (registration_starts_at < registration_ends_at
        AND registration_ends_at < exam_starts_at AND exam_starts_at < exam_ends_at),
    CONSTRAINT ck_session_capacity CHECK (capacity BETWEEN 1 AND 100000 AND registered_count <= capacity),
    CONSTRAINT ck_session_ticket_publication CHECK ((ticket_published_at IS NULL) = (ticket_published_by IS NULL)),
    CONSTRAINT ck_session_score_publication CHECK ((score_published_at IS NULL) = (score_published_by IS NULL)),
    CONSTRAINT fk_session_project_site FOREIGN KEY (project_id, site_id)
        REFERENCES exam_project_site (project_id, site_id),
    CONSTRAINT fk_session_ticket_publisher FOREIGN KEY (ticket_published_by) REFERENCES app_user (id),
    CONSTRAINT fk_session_score_publisher FOREIGN KEY (score_published_by) REFERENCES app_user (id),
    KEY ix_session_site_exam_time (site_id, exam_starts_at, id),
    KEY ix_session_public_exam_time (status, exam_starts_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE registration (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    registration_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    site_id BIGINT UNSIGNED NOT NULL,
    candidate_name_snapshot VARCHAR(50) NOT NULL,
    candidate_type_snapshot ENUM('STUDENT', 'SOCIAL') NOT NULL,
    school_id_snapshot BIGINT UNSIGNED NULL,
    school_name_snapshot VARCHAR(100) NULL,
    status ENUM('REGISTERED') NOT NULL DEFAULT 'REGISTERED',
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_registration_number UNIQUE (registration_number),
    CONSTRAINT uq_registration_candidate_project UNIQUE (candidate_id, project_id),
    CONSTRAINT uq_registration_id_session_site UNIQUE (id, session_id, site_id),
    CONSTRAINT ck_registration_school CHECK (
        (candidate_type_snapshot = 'STUDENT' AND school_id_snapshot IS NOT NULL AND school_name_snapshot IS NOT NULL)
        OR (candidate_type_snapshot = 'SOCIAL' AND school_id_snapshot IS NULL AND school_name_snapshot IS NULL)
    ),
    CONSTRAINT fk_registration_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (candidate_id),
    CONSTRAINT fk_registration_session FOREIGN KEY (session_id, project_id, site_id)
        REFERENCES exam_session (id, project_id, site_id),
    CONSTRAINT fk_registration_school FOREIGN KEY (school_id_snapshot) REFERENCES school (id),
    KEY ix_registration_candidate_time (candidate_id, submitted_at DESC, id DESC),
    KEY ix_registration_session_time (session_id, submitted_at DESC, id DESC),
    KEY ix_registration_site_time (site_id, submitted_at DESC, id DESC),
    KEY ix_registration_submitted (submitted_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_arrangement (
    registration_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    session_id BIGINT UNSIGNED NOT NULL,
    site_id BIGINT UNSIGNED NOT NULL,
    room_id BIGINT UNSIGNED NOT NULL,
    seat_number INT UNSIGNED NOT NULL,
    ticket_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_arrangement_seat UNIQUE (session_id, room_id, seat_number),
    CONSTRAINT uq_arrangement_ticket UNIQUE (ticket_number),
    CONSTRAINT ck_arrangement_seat_positive CHECK (seat_number > 0),
    CONSTRAINT fk_arrangement_registration FOREIGN KEY (registration_id, session_id, site_id)
        REFERENCES registration (id, session_id, site_id),
    CONSTRAINT fk_arrangement_room FOREIGN KEY (room_id, site_id) REFERENCES exam_room (id, site_id),
    CONSTRAINT fk_arrangement_actor FOREIGN KEY (updated_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_score (
    registration_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    score DECIMAL(7,2) NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_score_nonnegative CHECK (score >= 0),
    CONSTRAINT fk_score_registration FOREIGN KEY (registration_id) REFERENCES registration (id),
    CONSTRAINT fk_score_actor FOREIGN KEY (updated_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE score_import (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT UNSIGNED NOT NULL,
    imported_by BIGINT UNSIGNED NOT NULL,
    applied_count INT UNSIGNED NOT NULL,
    failed_count INT UNSIGNED NOT NULL,
    imported_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_import_session FOREIGN KEY (session_id) REFERENCES exam_session (id),
    CONSTRAINT fk_import_actor FOREIGN KEY (imported_by) REFERENCES app_user (id),
    KEY ix_import_session_time (session_id, imported_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notice (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category ENUM('POLICY', 'NOTICE', 'GUIDE', 'FAQ') NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    publisher_label VARCHAR(100) NOT NULL,
    pinned TINYINT UNSIGNED NOT NULL DEFAULT 0,
    featured TINYINT UNSIGNED NOT NULL DEFAULT 0,
    published_at DATETIME(6) NULL,
    status ENUM('DRAFT', 'PUBLISHED', 'WITHDRAWN') NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    withdrawn_at DATETIME(6) NULL,
    CONSTRAINT ck_notice_flags CHECK (pinned IN (0, 1) AND featured IN (0, 1)),
    KEY ix_notice_public (pinned DESC, published_at DESC, id DESC),
    KEY ix_notice_category_public (category, pinned DESC, published_at DESC, id DESC),
    KEY ix_notice_featured_public (featured, pinned DESC, published_at DESC, id DESC),
    KEY ix_notice_admin_status_updated (status, updated_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notice_attachment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notice_id BIGINT UNSIGNED NOT NULL,
    storage_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(127) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    byte_count BIGINT UNSIGNED NOT NULL,
    withdrawn_at DATETIME(6) NULL,
    KEY ix_attachment_notice_visibility (notice_id, withdrawn_at),
    CONSTRAINT uq_attachment_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_attachment_notice FOREIGN KEY (notice_id) REFERENCES notice (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_role (
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_permission (
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A role defines a permission ceiling for each scope, not an automatic grant.
CREATE TABLE auth_role_permission (
    role_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type ENUM('SYSTEM', 'CATALOG', 'PROJECT', 'SESSION') NOT NULL,
    permission_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (role_code, scope_type, permission_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_code) REFERENCES auth_role (code),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_code) REFERENCES auth_permission (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff_grant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    staff_id BIGINT UNSIGNED NOT NULL,
    account_kind ENUM('CANDIDATE', 'STAFF') NOT NULL DEFAULT 'STAFF',
    role_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type ENUM('SYSTEM', 'CATALOG', 'PROJECT', 'SESSION') NOT NULL,
    project_id BIGINT UNSIGNED NULL,
    session_id BIGINT UNSIGNED NULL,
    scope_key BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(project_id, session_id, 0)) STORED,
    granted_by BIGINT UNSIGNED NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_by BIGINT UNSIGNED NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT uq_grant_scope UNIQUE (staff_id, role_code, scope_type, scope_key),
    CONSTRAINT uq_grant_id_role_scope UNIQUE (id, role_code, scope_type),
    CONSTRAINT ck_grant_staff CHECK (account_kind = 'STAFF'),
    CONSTRAINT ck_grant_scope CHECK (
        (role_code = 'SYSTEM_ADMIN' AND scope_type = 'SYSTEM' AND project_id IS NULL AND session_id IS NULL)
        OR (role_code = 'ORGANIZER' AND scope_type = 'CATALOG' AND project_id IS NULL AND session_id IS NULL)
        OR (role_code = 'ORGANIZER' AND scope_type = 'PROJECT' AND project_id IS NOT NULL AND session_id IS NULL)
        OR (role_code IN ('ORGANIZER', 'SITE_MANAGER') AND scope_type = 'SESSION'
            AND project_id IS NULL AND session_id IS NOT NULL)
    ),
    CONSTRAINT ck_grant_revocation CHECK ((revoked_by IS NULL) = (revoked_at IS NULL)
        AND (revoked_at IS NULL OR revoked_at >= granted_at)),
    CONSTRAINT fk_grant_staff FOREIGN KEY (staff_id, account_kind) REFERENCES app_user (id, account_kind),
    CONSTRAINT fk_grant_role FOREIGN KEY (role_code) REFERENCES auth_role (code),
    CONSTRAINT fk_grant_project FOREIGN KEY (project_id) REFERENCES exam_project (id),
    CONSTRAINT fk_grant_session FOREIGN KEY (session_id) REFERENCES exam_session (id),
    CONSTRAINT fk_grant_issuer FOREIGN KEY (granted_by) REFERENCES app_user (id),
    CONSTRAINT fk_grant_revoker FOREIGN KEY (revoked_by) REFERENCES app_user (id),
    KEY ix_grant_actor_active (staff_id, revoked_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff_grant_permission (
    grant_id BIGINT UNSIGNED NOT NULL,
    role_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type ENUM('SYSTEM', 'CATALOG', 'PROJECT', 'SESSION') NOT NULL,
    permission_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (grant_id, permission_code),
    CONSTRAINT fk_grant_permission_grant FOREIGN KEY (grant_id, role_code, scope_type)
        REFERENCES staff_grant (id, role_code, scope_type),
    CONSTRAINT fk_grant_permission_ceiling FOREIGN KEY (role_code, scope_type, permission_code)
        REFERENCES auth_role_permission (role_code, scope_type, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT UNSIGNED NULL,
    actor_label VARCHAR(80) NOT NULL,
    action_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    scope_label VARCHAR(120) NULL,
    summary JSON NULL,
    result ENUM('SUCCESS', 'FAILED') NOT NULL,
    request_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user (id),
    KEY ix_audit_time (occurred_at DESC, id DESC),
    KEY ix_audit_target_time (target_type, target_id, occurred_at DESC, id DESC),
    KEY ix_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
