-- MySQL 8.0.44. Apply once to an existing schema after the notice admin migration.
-- NULL keeps existing attachments visible whenever their parent notice is public.
ALTER TABLE notice_attachment
    ADD COLUMN withdrawn_at DATETIME(6) NULL,
    ADD KEY ix_attachment_notice_visibility (notice_id, withdrawn_at);
