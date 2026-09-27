# EVOPS 基础工作区

合作社作业管理系统骨架（F1：农机底账 + 作业单主链路；F2：卫星轨迹核算平台每日对账）。

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

## 对账约定（F2）

- **每日勾稽**：Quartz 定时（`evops.recon.cron`，默认每天 06:00 对前一日的账，T+1）；本地账取当日登记轨迹面积的作业单（含红冲负面积单），对方账由卫星平台推送导入。
- **一笔对一笔**：按作业单号配对；对方同单号多条按序配对，剩余记对方单边差异。
- **差异清单**：只放差异（双边面积不符 / 单方缺账），双方明细以勾稽时刻快照留存；重跑不删历史，旧批次与其未核销差异置 SUPERSEDED，已核销保留。
- **核销**：手工调整唯一入口，核销单记全额调整 + 原因 + 操作人，与差异清零同一事务，核销后差异即时清零；一笔差异只能核销一次（`uk_recon_writeoff_diff` 兜底）。
- **新旧并跑**：旧引擎严格等额、新引擎容差算平，每次勾稽双引擎都跑，汇总落 `t_recon_compare`；`evops.recon.active-engine` 决定哪套出正式清单，连续两天 `identical=1` 后切 `TOLERANCE`。
- **容差可配置**：`evops.recon.tolerance-area`（亩），|本侧-对方| ≤ 容差算平，改配置重启生效。

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

对账 `/api/recon`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/recon/sat-bills/import` | 导入卫星平台账单明细（requestNo、billDate、lines[]，整批幂等） |
| POST | `/api/recon/run?bizDate=` | 手工触发勾稽（默认前一日，补跑/联调） |
| GET | `/api/recon/runs?bizDate=` | 勾稽批次列表 |
| GET | `/api/recon/diffs?bizDate=&status=` | 差异清单（带双方明细，状态默认 OPEN） |
| POST | `/api/recon/diffs/{id}/writeoff` | 核销（requestNo、reason、operator），核销后差异即时清零 |
| GET | `/api/recon/compares?from=&to=` | 新旧引擎并跑对比（连续两天 identical=1 后切 active-engine） |

表结构：F1 四张业务表（`t_machine`、`t_work_order`、`t_work_order_status_log`、`t_request_record`）+ F2 五张对账表（`t_sat_bill_line`、`t_recon_run`、`t_recon_diff`、`t_recon_writeoff`、`t_recon_compare`）。
