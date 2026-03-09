ALTER TABLE article_draft
ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1 draft, 2 published, 3 deleted' AFTER article_id;

CREATE INDEX idx_article_draft_user_status_updated_at
ON article_draft(user_id, status, updated_at);
