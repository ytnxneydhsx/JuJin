ALTER TABLE article
ADD COLUMN like_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Like count',
ADD COLUMN favorite_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Favorite count';

CREATE TABLE IF NOT EXISTS article_like (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'User id (user_base.id)',
  article_id BIGINT UNSIGNED NOT NULL COMMENT 'Article id (article.id)',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 0 cancelled, 1 active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_article_like_user_article (user_id, article_id),
  KEY idx_article_like_article_status (article_id, status),
  KEY idx_article_like_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS article_favorite (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'User id (user_base.id)',
  article_id BIGINT UNSIGNED NOT NULL COMMENT 'Article id (article.id)',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 0 cancelled, 1 active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_article_favorite_user_article (user_id, article_id),
  KEY idx_article_favorite_article_status (article_id, status),
  KEY idx_article_favorite_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
