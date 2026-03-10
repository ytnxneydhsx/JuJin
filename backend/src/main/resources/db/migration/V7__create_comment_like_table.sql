CREATE TABLE IF NOT EXISTS comment_like (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'User id (user_base.id)',
  comment_id BIGINT UNSIGNED NOT NULL COMMENT 'Comment id (article_comment.id)',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 0 cancelled, 1 active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_like_user_comment (user_id, comment_id),
  KEY idx_comment_like_comment_status (comment_id, status),
  KEY idx_comment_like_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
