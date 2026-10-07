# 服务端可信前缀登记候选

代码：root 0c48602；私有产品 217e5cd。完整提交树构建：内核330/0/0/0、产品760/0/0/0，product SHA04744751250fd7cfadc9669647f5c2d20bee5de8eb68ab5e4aa88ee87520820f，kernel SHA61288022bcf9d1cfe860a436387fe0acda6371cd7de18732dea5e6abd8c74f1a。adapter与SPI class未变。

实验使用专属源已有可信产品获取101320533446070272、冻结75 capture；只读复用原登记/原snapshot，metadata完全相等。新控制库ORKPFXCTL_20261007，helper ork-prefix-runtime-20261007，端口37267。没有新源业务DML、获取或日志配置。

实际认证HTTP生成1 pending/0 completed状态1135字节，SHA7d6eef2ad4439bdad1ec93a853de95b36810cb334637db81fe588022ba1b6512。控制库CHECK约束注入登记失败：应用code409/ORACLE_PREFIX_CHECKPOINT_REFUSED，行仍APPROVED_PREFIX、成功审计0，已force状态文件保留。解除约束后复用该文件登记一次，重复请求字节和审计计数不变。SIGKILL后exit137，同件重启GET核原始来源/状态通过。状态毒化实际code409/ORACLE_PREFIX_RECOVERY_REFUSED，精确恢复后GET通过。任务/窗口/批次/事件/发布门均0，ACK0；最终helper停止exit143。

新失败原始HTTP响应及进程输出均保留，但服务只登记通用reason/id，底层SQL异常没有持久化。**这项失败原文保全缺口未关闭**，不声称已证明原始ORA原因。初次health在启动完成前连接拒绝，保留stderr/rc1；启动完成后同件健康通过，没有重复建库或换件。

证据：`docs/workorders/evidence/DELIVERY-product-prefix-registration-20261007.json`。制品目录`lab/out/product-prefix-registration-20261007`、运行目录`lab/out/product-prefix-runtime-20261007`封存，只读；秘密排除SHA/提交。

| 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|
| 通过 | 通过 | 进行中：仅Oracle显式前缀API | 待证 | 待证 | 待证 |

未通过：自动PipelineFacade pending流程、完整75/76通过prefixBindingId引用的产品发布链、并发/两进程、MySQL/SQLServer同场景、独立评审、一般矩阵、独立目标/现场/发布。D1不关闭。下一修复异常留证，继续同冻结输入的完整发布集成。
