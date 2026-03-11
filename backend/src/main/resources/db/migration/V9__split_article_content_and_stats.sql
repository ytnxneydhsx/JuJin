CREATE TABLE IF NOT EXISTS article_content (
  article_id BIGINT UNSIGNED NOT NULL COMMENT 'Article id (article.id)',
  content LONGTEXT NOT NULL COMMENT 'Article content',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS article_stats (
  article_id BIGINT UNSIGNED NOT NULL COMMENT 'Article id (article.id)',
  like_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Like count',
  favorite_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Favorite count',
  view_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'View count',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (article_id),
  KEY idx_article_stats_view_count (view_count),
  KEY idx_article_stats_like_count (like_count),
  KEY idx_article_stats_favorite_count (favorite_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO article_content(article_id, content, created_at, updated_at)
SELECT
  a.id,
  a.content,
  a.created_at,
  a.updated_at
FROM article a
ON DUPLICATE KEY UPDATE
  content = VALUES(content),
  updated_at = VALUES(updated_at);

INSERT INTO article_stats(article_id, like_count, favorite_count, view_count, created_at, updated_at)
SELECT
  a.id,
  COALESCE(a.like_count, 0),
  COALESCE(a.favorite_count, 0),
  COALESCE(a.view_count, 0),
  a.created_at,
  a.updated_at
FROM article a
ON DUPLICATE KEY UPDATE
  like_count = VALUES(like_count),
  favorite_count = VALUES(favorite_count),
  view_count = VALUES(view_count),
  updated_at = VALUES(updated_at);
