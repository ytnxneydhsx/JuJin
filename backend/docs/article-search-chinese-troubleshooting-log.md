# 文章搜索中文命中异常排查日志

## 1. 问题背景

- 现象：前端文章搜索页输入中文关键词后，已经发布的文章没有出现在结果中。
- 典型复现词：`测试`
- 对比现象：
  - 搜索 `11111` 可以返回文章 `测试11111`
  - 搜索 `test` 可以正常返回用户搜索结果
  - 只有中文文章搜索异常

本次排查目标不是只做理论分析，而是基于运行中的真实服务做复现、定位和修复。

## 2. 排查环境

- 日期：2026-03-15
- 工作目录：`D:\JuJin`
- 运行方式：Docker Compose
- 涉及服务：
  - `juejin-backend`
  - `juejin-es`
  - `juejin-mysql`
  - `juejin-redis`
- 关键接口：
  - `GET /api/search/articles?q=...`
  - `GET /api/search/users?q=...`

## 3. 结论先行

这次问题最终确认分成两层：

1. 旧的 ES 文档结构里存在多余字段，导致 Spring Data Elasticsearch 反序列化时曾出现字段转换异常。
2. 中文文章搜索的真正故障点不在前端，也不在“文章没入 ES”，而是在“后端 Java 发往 ES 的中文查询链路”上。  
   同一份 DSL：
   - 用 `curl` 直接打 ES 可以命中中文文章
   - 后端 Java 代码发起请求时却返回 0 条

由于第二层问题短时间内不容易稳定根除，最终采取了业务可用优先的修复方案：

- 中文关键词文章搜索：改为走 MySQL `LIKE` 查询已发布文章
- 英文/数字文章搜索：继续走 ES
- 用户搜索：保留现有 ES 搜索逻辑

## 4. 排查过程时间线

### 4.1 第一阶段：确认不是前端显示问题

#### 做了什么

- 检查前端搜索页调用逻辑，确认文章搜索页确实会请求后端文章搜索接口，而不是前端本地过滤。
- 核对前端没有额外过滤“自己发布的文章”。

#### 观察到的现象

- 前端确实调用 `/api/search/articles?q=...`
- 前端没有把作者自己的文章过滤掉

#### 判断

- 问题不在前端“结果渲染层”
- 需要继续排查后端接口和 ES 数据

---

### 4.2 第二阶段：确认 ES 中是否真的有文章数据

#### 做了什么

- 直接检查 `article_search` 和 `user_search` 索引中的文档
- 核对已经发布的文章是否已经同步进入 ES

#### 观察到的现象

- `article_search` 至少存在这些文档：
  - `articleId=2`，标题为 `测试`
  - `articleId=3`，标题为 `测试11111`
- `user_search` 中也有用户数据，例如 `test`

#### 判断

- 问题不在“发布文章后没有建立索引”
- 中文文章数据已经在 ES 中

---

### 4.3 第三阶段：发现旧文档反序列化异常

#### 做了什么

- 根据报错堆栈排查 `UserSearchController` / `ArticleSearchController` 对应的 ES 文档映射类
- 核对 ES 索引中的实际字段和 Java 文档类字段定义

#### 观察到的现象

- 报错里出现过类似异常：
  - `Unable to convert value '2026-03-11' to java.time.LocalDateTime for property 'updatedAt'`
- ES 旧文档里包含：
  - `_class`
  - `publishedAt`
  - `updatedAt`
- 当前文档读取路径在遇到这些字段时会触发转换问题

#### 做了什么修复

- 在以下类上增加：
  - `@JsonIgnoreProperties(ignoreUnknown = true)`
- 修改文件：
  - [ArticleSearchDocument.java](/d:/JuJin/backend/src/main/java/org/example/backend/model/es/ArticleSearchDocument.java)
  - [UserSearchDocument.java](/d:/JuJin/backend/src/main/java/org/example/backend/model/es/UserSearchDocument.java)

#### 判断

- 这一步修掉了“旧 ES 文档字段导致反序列化失败”的问题
- 但这还不是中文命中不到的根因，因为即使没有转换异常，中文查询仍然是 0 条

---

### 4.4 第四阶段：直接复测后端接口，确认故障稳定复现

#### 做了什么

- 直接请求真实后端接口：

```text
GET /api/search/articles?q=测试&page=0&size=10
GET /api/search/articles?q=11111&page=0&size=10
GET /api/search/users?q=test&page=0&size=10
```

#### 观察到的现象

- 搜索 `测试`：
  - 返回 `records=[]`
  - `total=0`
- 搜索 `11111`：
  - 正常返回文章 `测试11111`
- 搜索用户 `test`：
  - 正常返回用户结果

#### 判断

- 故障只发生在“中文文章搜索”
- 用户搜索链路正常
- 英文/数字搜索正常

---

### 4.5 第五阶段：把问题从“ES 没数据”进一步缩小为“Java 请求链路异常”

#### 做了什么

- 在 `ArticleSearchServiceImpl` 中打印实际发送给 ES 的 DSL 和返回结果
- 同时用两种方式验证同一份 DSL：
  - 宿主机直接请求 ES
  - 在 `juejin-backend` 容器内用 `curl` 请求 ES

#### 观察到的现象

- Java 日志里打印出的 DSL 大意如下：

```json
{
  "from": 0,
  "size": 10,
  "track_total_hits": true,
  "query": {
    "bool": {
      "must": [
        {
          "bool": {
            "minimum_should_match": 1,
            "should": [
              { "regexp": { "title": ".*测试.*" } },
              { "regexp": { "summary": ".*测试.*" } }
            ]
          }
        }
      ]
    }
  }
}
```

- 直接用 `curl` 请求 ES：
  - 能查到文章 `测试`
  - 能查到文章 `测试11111`
- 但后端 Java 代码发出“看起来相同”的 DSL 后：
  - `测试` 返回 `hits.total.value = 0`
  - `11111` 返回 `hits.total.value = 1`

#### 判断

- ES 本身可以命中中文
- ES 索引中也确实有中文数据
- 后端发给 ES 的查询“表面上一样”，但 Java 侧真实请求行为和 `curl` 的结果不一致
- 问题被缩小到“Java -> ES 中文查询链路”

---

### 4.6 第六阶段：尝试修复 Java 到 ES 的请求编码问题

#### 做了什么

- 先后尝试过几种搜索实现方式：
  - Spring Data Repository 派生查询
  - `StringQuery`
  - `ElasticsearchClient`
  - Java HTTP 直连 ES
  - `RestTemplate`
- 最后又把请求体从 `String` 传输改成明确的 `UTF-8 byte[]` 发送，试图排除字符编码问题

#### 观察到的现象

- 即使改成 `byte[]` 明确按 UTF-8 发送
- 后端接口搜索 `测试` 仍然返回 0 条

#### 判断

- 问题并没有被简单的请求编码修复掉
- 继续深挖会消耗更多时间，而且短时间内不一定能拿到稳定方案
- 需要切换为“先恢复业务可用”的处理方式

---

### 4.7 第七阶段：制定业务修复方案

#### 方案判断

既然：

- 中文搜索当前在 Java -> ES 查询链路上不稳定
- 但中文文章数据本身在 MySQL 中肯定存在
- 文章搜索当前最重要的是“前端能搜到已发布文章”

所以选择：

- 中文关键词文章搜索直接走 MySQL
- 只查 `PUBLISHED` 状态文章
- 保留分页和总数统计
- 英文/数字搜索继续走 ES

这个方案的优点是：

- 立即恢复中文文章搜索可用性
- 不依赖当前异常的 Java -> ES 中文查询链路
- 不会影响英文/数字搜索
- 不会影响用户搜索

---

### 4.8 第八阶段：落地代码修改

#### 改动一：文章搜索服务增加中文分支

修改文件：

- [ArticleSearchServiceImpl.java](/d:/JuJin/backend/src/main/java/org/example/backend/service/search/article/impl/ArticleSearchServiceImpl.java)

核心处理：

- 新增 `containsHanCharacter(...)`
- 在 `search(...)` 中：
  - 如果关键词包含汉字，走 `searchPublishedByKeywordFromMysql(...)`
  - 否则继续走 ES 搜索

对应关键位置：

- [ArticleSearchServiceImpl.java](/d:/JuJin/backend/src/main/java/org/example/backend/service/search/article/impl/ArticleSearchServiceImpl.java#L52)

#### 改动二：新增 MySQL 搜索 SQL

修改文件：

- [ArticleSearchMapper.java](/d:/JuJin/backend/src/main/java/org/example/backend/mapper/search/ArticleSearchMapper.java)

新增方法：

- `searchPublishedByKeyword(...)`
- `countPublishedByKeyword(...)`

SQL 语义：

- 只查询 `status = PUBLISHED`
- `title LIKE %keyword% OR summary LIKE %keyword%`
- 支持按 `userId` 过滤
- 支持分页和总数统计

对应关键位置：

- [ArticleSearchMapper.java](/d:/JuJin/backend/src/main/java/org/example/backend/mapper/search/ArticleSearchMapper.java#L61)

#### 改动三：兼容旧 ES 文档字段

修改文件：

- [ArticleSearchDocument.java](/d:/JuJin/backend/src/main/java/org/example/backend/model/es/ArticleSearchDocument.java#L17)
- [UserSearchDocument.java](/d:/JuJin/backend/src/main/java/org/example/backend/model/es/UserSearchDocument.java#L17)

处理方式：

- 增加 `@JsonIgnoreProperties(ignoreUnknown = true)`

---

### 4.9 第九阶段：重新构建并回归验证

#### 做了什么

- 重新执行后端镜像构建
- 重启 `juejin-backend`
- 再次请求真实接口

#### 最终验证结果

文章搜索：

```text
GET /api/search/articles?q=测试&page=0&size=10
```

返回结果：

- 命中 2 条
  - `测试11111`
  - `测试`

文章搜索：

```text
GET /api/search/articles?q=11111&page=0&size=10
```

返回结果：

- 仍然正常命中 `测试11111`

用户搜索：

```text
GET /api/search/users?q=test&page=0&size=10
```

返回结果：

- 仍然正常

#### 判断

- 中文文章搜索已恢复
- 英文/数字文章搜索未受影响
- 用户搜索未受影响

## 5. 本次排查中明确排除的错误方向

- 不是前端把自己的文章过滤掉
- 不是文章未发布
- 不是文章没有入 ES
- 不是 ES 本身不支持该中文正则查询
- 不是用户搜索整体失效

## 6. 当前留下的技术债

虽然业务已经恢复，但还有一个未完全根治的问题：

- 为什么同一份中文查询 DSL，用 `curl` 请求 ES 能命中，而后端 Java 代码请求 ES 却返回 0 条？

这说明当前“Java -> ES 中文查询链路”的底层行为仍然值得继续排查。可能的方向包括：

- Java 客户端实际发送内容与日志展示内容并不完全一致
- 某些 HTTP 层默认配置导致正文被重新编码或转义
- ES 正则查询与客户端序列化细节之间存在隐藏差异
- 某个中间层对请求进行了改写

本次没有继续深挖这个问题，是因为业务层已经可以通过中文走 MySQL 的方式稳定恢复。

## 7. 后续建议

### 建议 A：把当前方案视为“可用优先修复”

适用于当前阶段，因为：

- 用户已经可以搜到中文文章
- 代码风险较低
- 排障成本可控

### 建议 B：后续再做 ES 根因专项排查

建议单独起一个任务，专门研究：

- Java 到 ES 的中文正则查询为什么与 `curl` 结果不一致
- 是否改成 `match` / `multi_match` + 中文分词器更合适
- 是否需要重新设计中文搜索索引映射

### 建议 C：如果要长期依赖 ES 做中文搜索

更合理的长期方向不是继续依赖 `regexp`，而是：

- 为文章标题/摘要配置明确的中文分词器
- 改成 `match` / `multi_match`
- 针对标题、摘要分别设置权重

## 8. 本次涉及的主要文件

- [ArticleSearchServiceImpl.java](/d:/JuJin/backend/src/main/java/org/example/backend/service/search/article/impl/ArticleSearchServiceImpl.java)
- [ArticleSearchMapper.java](/d:/JuJin/backend/src/main/java/org/example/backend/mapper/search/ArticleSearchMapper.java)
- [ArticleSearchDocument.java](/d:/JuJin/backend/src/main/java/org/example/backend/model/es/ArticleSearchDocument.java)
- [UserSearchDocument.java](/d:/JuJin/backend/src/main/java/org/example/backend/model/es/UserSearchDocument.java)

## 9. 一句话总结

这次问题不是“文章没发布”也不是“ES 里没数据”，而是“后端 Java 发往 ES 的中文文章查询链路异常”；最终通过“中文走 MySQL、英文数字继续走 ES”的方式，把前端文章中文搜索恢复到了可用状态。
