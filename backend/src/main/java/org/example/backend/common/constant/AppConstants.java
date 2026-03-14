package org.example.backend.common.constant;

public final class AppConstants {

    private AppConstants() {
    }

    public static final class RelationStatus {

        public static final int CANCELLED = 0;
        public static final int ACTIVE = 1;

        private RelationStatus() {
        }
    }

    public static final class ArticleStatus {

        public static final int PUBLISHED = 1;
        public static final int HIDDEN = 2;
        public static final int DELETED = 3;

        private ArticleStatus() {
        }
    }

    public static final class DraftStatus {

        public static final int DRAFT = 1;
        public static final int PUBLISHED = 2;
        public static final int DELETED = 3;

        private DraftStatus() {
        }
    }

    public static final class CommentStatus {

        public static final int NORMAL = 1;
        public static final int DELETED = 2;

        private CommentStatus() {
        }
    }

    public static final class UserStatus {

        public static final int ACTIVE = 1;

        private UserStatus() {
        }
    }

    public static final class ArticleSort {

        public static final String BY_PUBLISHED_AT = "publishedAt";
        public static final String BY_VIEW_COUNT = "viewCount";
        public static final String ORDER_ASC = "asc";
        public static final String ORDER_DESC = "desc";

        private ArticleSort() {
        }
    }

    public static final class UploadBizType {

        public static final String ARTICLE_COVER = "article_cover";
        public static final String ARTICLE_CONTENT = "article_content";
        public static final String USER_AVATAR = "user_avatar";

        private UploadBizType() {
        }
    }
}
