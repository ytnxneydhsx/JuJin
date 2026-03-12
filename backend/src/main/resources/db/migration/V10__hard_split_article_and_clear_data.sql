SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE comment_like;
TRUNCATE TABLE article_comment;
TRUNCATE TABLE article_like;
TRUNCATE TABLE article_favorite;
TRUNCATE TABLE article_draft;
TRUNCATE TABLE article_content;
TRUNCATE TABLE article_stats;
TRUNCATE TABLE article;
TRUNCATE TABLE user_profile;
TRUNCATE TABLE user_base;

SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE article
  DROP COLUMN content,
  DROP COLUMN like_count,
  DROP COLUMN favorite_count,
  DROP COLUMN view_count;
