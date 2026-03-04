-- Article table (published / managed articles)
CREATE TABLE IF NOT EXISTS article (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'Author user id (user_base.id)',
  title VARCHAR(200) NOT NULL COMMENT 'Article title',
  summary VARCHAR(500) DEFAULT NULL COMMENT 'Article summary',
  cover_url VARCHAR(512) DEFAULT NULL COMMENT 'Cover image URL',
  content LONGTEXT NOT NULL COMMENT 'Article content',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 published, 2 hidden, 3 deleted',
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Publish time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  KEY idx_article_user_id (user_id),
  KEY idx_article_status_published_at (status, published_at),
  KEY idx_article_user_published_at (user_id, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Draft table (draft box)
CREATE TABLE IF NOT EXISTS article_draft (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id (user_base.id)',
  article_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Related article id when editing an existing article',
  title VARCHAR(200) DEFAULT NULL COMMENT 'Draft title',
  summary VARCHAR(500) DEFAULT NULL COMMENT 'Draft summary',
  cover_url VARCHAR(512) DEFAULT NULL COMMENT 'Draft cover URL',
  content LONGTEXT DEFAULT NULL COMMENT 'Draft content',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  KEY idx_article_draft_user_id (user_id),
  KEY idx_article_draft_user_updated_at (user_id, updated_at),
  KEY idx_article_draft_article_id (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
