# ES 搜索模式（MySQL + Elasticsearch）

本文说明为什么项目采用 MySQL 作为数据主库、Elasticsearch 作为搜索索引，以及后续模块如何复用这套模式。

## 1. 为什么不直接“用 ES 代替数据表”

Elasticsearch 很适合文本搜索和排序，但不适合作为核心事务数据的唯一存储。

- MySQL 更适合强约束、事务和一致性。
- ES 更适合模糊搜索、全文检索和打分排序。
- 生产环境常见方案是：
  - MySQL：业务数据读写主库
  - ES：面向搜索的字段副本

在本项目中：

- 用户主数据在 `user_base` 和 `user_profile`。
- 搜索数据在 ES 索引 `user_search`。

## 2. 本项目当前实现

### 数据流

1. 通过 `UserSearchMapper` 从 MySQL（`user_base` + `user_profile`）读取用户搜索数据。
2. 将关系型数据转换为 `UserSearchDocument`。
3. 写入 ES 索引 `user_search`。
4. 搜索接口只查询 ES。

### 已添加代码

- Mapper 投影查询：
  - `src/main/java/org/example/backend/mapper/search/UserSearchMapper.java`
- 中间模型：
  - `src/main/java/org/example/backend/model/dto/UserSearchSource.java`
- ES 文档模型：
  - `src/main/java/org/example/backend/model/es/UserSearchDocument.java`
- ES Repository：
  - `src/main/java/org/example/backend/repository/UserSearchRepository.java`
- Service（搜索 + 全量重建 + 单条同步）：
  - `src/main/java/org/example/backend/service/search/UserSearchService.java`
  - `src/main/java/org/example/backend/service/search/impl/UserSearchServiceImpl.java`
- Controller 接口：
  - `src/main/java/org/example/backend/controller/UserSearchController.java`

## 3. 当前可用接口

- 搜索用户：
  - `GET /api/search/users?q=keyword&page=0&size=20`
- 从 MySQL 全量重建索引：
  - `POST /api/search/users/rebuild-index`
- 按账号增量同步：
  - `POST /api/search/users/sync/{account}`

## 4. 如何复用到其他模块

帖子、标签、评论等都可以套用同一模板。

1. 先定义搜索场景。
   - 用户要按什么搜？标题/正文/标签/作者？
2. 设计 ES 文档结构。
   - 只保留搜索和结果展示所需字段。
   - 不存敏感字段。
3. 写 MySQL 投影查询。
   - 用一条 mapper 查询返回 ES 需要的完整字段。
4. 写转换器和同步服务。
   - `source -> document`
   - 必须提供：
     - 全量重建（修复用）
     - 单条同步（增量用）
5. 暴露搜索接口。
   - 统一各模块的查询和分页参数。
6. 在业务流程中接入同步触发。
   - MySQL 的新增/更新/删除后调用同步方法。

## 5. 同步策略选择

### 阶段 A（当前够用、实现简单）

- 同步双写：
  - 先写 MySQL
  - 再写 ES
- 保留 `rebuild-index` 接口用于故障恢复。

### 阶段 B（后续扩展）

- 事件驱动异步同步（MQ / binlog / outbox）。
- 增加重试和死信处理。

## 6. 常见坑

- 把 ES 当主库。
- 把密码或隐私字段写入 ES 文档。
- 没有全量重建机制。
- 写死了集群里没有安装的分词插件。
- 同步时忽略禁用/删除状态。

## 7. 新增可搜索模块检查清单

- [ ] MySQL 表和约束已定义
- [ ] ES 文档只包含搜索/展示字段
- [ ] Mapper 投影查询完整并验证
- [ ] 提供全量重建接口
- [ ] 提供增量同步方法
- [ ] 搜索接口支持分页
- [ ] 敏感数据已排除
- [ ] 故障恢复路径有文档

## 8. 配合 Docker ES 运行

`application.yaml` 使用：

```yaml
spring:
  elasticsearch:
    uris: ${ES_URIS:http://localhost:9200}
```

含义：

- 本地直接运行后端：默认使用 `http://localhost:9200`
- 后端也容器化时：设置 `ES_URIS=http://elasticsearch:9200`
