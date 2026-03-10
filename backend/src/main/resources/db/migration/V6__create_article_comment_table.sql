CREATE TABLE IF NOT EXISTS article_comment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  article_id BIGINT UNSIGNED NOT NULL COMMENT 'Article id (article.id)',
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'Comment author user id (user_base.id)',
  root_id BIGINT UNSIGNED NOT NULL COMMENT 'Root comment id of the thread; top-level comment uses its own id',
  parent_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Direct parent comment id; top-level comment is NULL',
  reply_to_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Replied user id (optional)',
  content TEXT NOT NULL COMMENT 'Comment content',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 normal, 2 deleted',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  KEY idx_article_comment_article_root_status_created (article_id, root_id, status, created_at, id),
  KEY idx_article_comment_article_parent_status_created (article_id, parent_id, status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
