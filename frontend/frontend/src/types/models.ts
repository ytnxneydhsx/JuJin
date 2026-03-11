export interface UserRegisterVO {
  userId: number
  account: string
}

export interface UserPublicProfileVO {
  userId: number
  name: string
  avatarUrl: string | null
  sign: string | null
}

export interface ArticleSummaryVO {
  articleId: number
  userId: number
  title: string
  summary: string | null
  coverUrl: string | null
  likeCount: number
  favoriteCount: number
  viewCount: number
  liked: boolean
  favorited: boolean
  publishedAt: string
  updatedAt: string
}

export interface ArticleDetailVO extends ArticleSummaryVO {
  content: string
  status: number
  createdAt: string
}

export interface ArticleLikeVO {
  articleId: number
  liked: boolean
  likeCount: number
}

export interface ArticleFavoriteVO {
  articleId: number
  favorited: boolean
  favoriteCount: number
}

export interface ArticleCommentVO {
  commentId: number
  articleId: number
  userId: number
  rootId: number
  parentId: number | null
  replyToUserId: number | null
  content: string
  liked: boolean
  createdAt: string
  updatedAt: string
}

export interface CommentIdVO {
  commentId: number
}

export interface CommentLikeToggleVO {
  commentId: number
  liked: boolean
}

export interface ArticleDraftVO {
  draftId: number
  articleId: number | null
  title: string | null
  summary: string | null
  coverUrl: string | null
  content: string | null
  createdAt: string
  updatedAt: string
}

export interface DraftIdVO {
  draftId: number
}

export interface ArticleIdVO {
  articleId: number
}

export interface UploadImageVO {
  bizType: string
  key: string
  url: string
  size: number
  contentType: string
}

export interface ArticleSearchVO {
  articleId: number
  userId: number
  title: string
  summary: string | null
}

export interface UserSearchVO {
  id: number
  name: string
}
