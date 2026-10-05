# W6/W7 冻结新 DML → Oracle 原子目标提交 → checkpoint/ACK

本轮有限非空、NULL尾列、同事务混合/两全回滚前缀通过，独立纯回滚空窗口仍待证。没有重放源操作，没有重建内核或重跑251项接受测试，没有部署产品。证据目录 `lab/out/oracle-dml-target-20261005`，构建 `lab/out/release-oracle-dml-20261005`；[双次SHA/命令rc/制品/源码清单](evidence/DELIVERY-oracle-dml-target-20261005.json)。此前混合capture交付已推送2f89911。

## 制品及隔离

原a084218e5ebc9c4e3313cd84beba0c2bea4ff280a82f93fcf5890d2a0493f968内核复制到新私仓，用不可变版本 `0.1.0-bounded-4f79691` 安装；POM仅增加版本属性，默认仍原版本。SPI93e451fe…不变，新adapter029ffa13f52767093f60405d77f26ed545fe16ffa14514dd36dd3b1e985925d5，15模块测试0失败/错误/跳过。全部adapter运行时class与此前接受制品逐字节相同，已有INSERT/DELETE/NULL路径无需修改。旧私仓和旧制品保留，StreamHouse共享checkout未写。

## 专属目标实际消费

核专属 `oraredo-19c-lab-20261004` owner/running；仅目标用户ORKTGT_CMT_20261005新20表。DNON、DNUL、DMIX、DFAIL各五表，sourceBinding/schema绑定各实际可信基线，不能把三fixture拼成同一消费窗口。原始capture与基线SHA冻结复制，JDBC helper只执行Java，不部署Flink。目标仍同实例跨schema，不当独立生产目标。

| 目标 | 原始范围 | 每个递增前缀新增DML | 最终审计/成员/提交凭证 | 重复消费 |
|---|---|---|---|---|
| DNON | 44→45 | 1 INSERT、1 DELETE | 2/3/2 | 两前缀各0 |
| DNUL | 46→47 | 1 INSERT、1 DELETE，TXT/NOTE均NULL | 2/3/2 | 两前缀各0 |
| DMIX | 48→49→50 | 3（INSERT→UPDATE→DELETE）、0、0 | 3/9/3 | 三前缀各0 |
| DFAIL | 48—50完整范围 | 首次UPDATE约束拒绝；修复目标条件后3 | 3/3/1 | 再消费0 |

混合事务三中间像及操作全部记录，不用最终seed相等代替三次变更。两rollback窗口扩展没有新增业务事件，消费前沿仍从48推进49、50。每批目标业务DML、审计、成员、提交凭证同事务，之后才发布checkpoint/ACK；所有重复消费checkpoint原字节稳定。

DFAIL自有目标约束拒绝TXT=mixed-after：实际UPDATE抛ORA-02290、rc1/stdout0，前置INSERT及审计/成员/凭证一起回滚到0/0/0，seed保留，无checkpoint/ACK。仅移除此目标约束后原raw恢复3次应用并重复0，未重放源DML。不重跑旧SIGKILL矩阵，也不把本轮约束失败当新的进程崩溃证明。

## 独立核验与失败保全

独立比较器从此前native/SQL接受的原始EVENT/ROW输出生成完整typed像，实际四目标20个前后像、18成员、8提交凭证及checkpoint全部scope/基线/leaf/source/capture位置字段、ACK零差异。DNON和DNUL前缀实际SQL中间行分别非空/NULL，所有最终seed一致。缺UPDATE、错operation、错member ordinal三派生比较反例均拒绝，原证据不修改。新adapter公开decoder实际拒绝、stdout0。

首次DRBK探针多传inspect参数，NoSuchFileException:2、rc1原文和执行时脚本保留；只补失败项，不重跑DNON/DNUL/DMIX已接受投影。修正后49—50独立后缀被BASELINE_SCN_OUTSIDE_INPUT_COVERAGE拒绝，rc1/stdout0，因为原可信基线SCN2426700属于48。没有移动/伪造基线或丢弃窗口中事务。故本轮只认证48起的合法前缀中两全回滚零新增，不宣称独立空batch commit/ACK通过。封存时源码标明post-execution，首次失败源码单列，实际字节和旧HEAD blob分开。

源supplemental NO/NO、旧表11/blocking-closed/new-note及三个fixture seed前后SQL逐字节相同；0源DML/0日志配置变化，helper退出。自动化oracle-redo已实际绑定本chat，长期goal保持active。

下一步见[执行包](DISPATCH_W7_EMPTY_BATCH_AND_PRODUCT_20261005.md)。独立空batch、跨基线/活跃跨窗、生产可信字典、完整产品pipeline/C++ realtime Oracle、独立目标实例/断电/现场/发布均未完成。
