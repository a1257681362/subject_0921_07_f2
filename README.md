# EVOPS 基础工作区

合作社作业管理系统骨架（F1：农机底账 + 作业单主链路）。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动：

1. `mvn -q -DskipTests compile` 构建。
2. `mvn spring-boot:run` 启动；Spring Boot 自动执行 `src/main/resources/schema.sql`（对旧库幂等，`CREATE TABLE IF NOT EXISTS` + 版本登记 `MERGE`）。
3. H2 数据文件默认写入工作区 `data/evops`。测试使用独立内存库（`test` profile）。
4. `mvn test` 运行全部测试。

> 交付副本 `../../docs/schema/evops.sql` 应与 `src/main/resources/schema.sql` 保持一致；当前容器对根目录 `/docs` 无写权限，部署时由有权限的环节同步。

## 核心约定

- **统一返回结构**：`{ success, message, data }`；业务/参数异常同样 HTTP 200 + `success=false`。
- **请求号**：所有新增/变更请求必须携带 `requestNo`，先登记 `t_request_record`（唯一约束），与业务写入同一事务；重放/并发重复提交只接受第一笔。
- **版本**：农机、作业单带 `version` 乐观锁，变更须携带最新版本，落库自动递增。
- **不物理删除**：农机停用（DISABLED）、作业单红冲（REVERSED）均只改状态、保留历史。
- **面积口径**：`trackArea` 轨迹核算面积是权威依据，完工必须先有轨迹面积；`manualArea` 人工上报仅留痕参考。
- **红冲**：原单置 `REVERSED`，另开反向单 `单号-R`（面积取反、`reversalFlag=1`、`originOrderId` 指向原单）。
- **事务边界**：建档/流转/面积/红冲均为单事务，台账、主表、状态日志一起提交或整体回退。
- 同一作业单号并发两笔建档，由 `uk_work_order_no` 唯一约束兜底，只成功一条。

## 接口

农机 `/api/machines`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/machines` | 农机建档（machineCode、machineName、requestNo） |
| POST | `/api/machines/{id}` | 变更信息（version 乐观锁、requestNo） |
| POST | `/api/machines/{id}/disable` | 停用（requestNo） |
| GET | `/api/machines/{id}` | 查询 |
| GET | `/api/machines` | 列表 |

作业单 `/api/work-orders`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/work-orders` | 建档（orderNo、machineId、operationType、requestNo） |
| GET | `/api/work-orders?orderNo=xxx` | 按作业单号检索（作业单 + 状态流转日志） |
| POST | `/api/work-orders/{id}/transit` | 状态流转（CREATED→IN_PROGRESS→COMPLETED；CREATED/IN_PROGRESS→CANCELLED） |
| POST | `/api/work-orders/{id}/track-area` | 登记轨迹核算面积（权威） |
| POST | `/api/work-orders/{id}/manual-area` | 人工上报面积（仅参考） |
| POST | `/api/work-orders/{id}/reverse` | 红冲，另开反向单 |

表结构（4 张业务表）：`t_machine`、`t_work_order`、`t_work_order_status_log`、`t_request_record`。
