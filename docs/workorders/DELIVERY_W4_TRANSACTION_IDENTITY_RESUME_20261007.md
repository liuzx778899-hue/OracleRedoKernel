# W4 事务身份checkpoint与独立JVM恢复候选

kernel候选 SHA256 **44341f9da66956010962d4577d6c2972e3194c5d97882a16afa73a1603777a00**，完整 **317/0/0/0**，四项新增针对性测试通过；仅TransactionLifecycle生产class改变，另122个class字节不变。无新依赖，无源动作、获取、目标发布或产品换装。

新增public API `TransactionLifecycle.checkpoint()` / `restore(byte[], trustedSha256)`，只保存identity lifecycle：当前source/日志header、活跃事务UID/CID/segment/slot/full sequence/USN/begin record SCN、已关闭完整key。确定性排序、V1格式、规范字节往返；外部可信摘要验证后才恢复。重复key、活跃slot冲突、无效身份/SCN、截断及非规范格式拒绝。拒绝后不能再导出健康状态；恢复后的source identity、sequence/SCN连续、未闭合finish、重复end等门禁不变。

**接口边界：**调用方须可靠持久保存状态字节、可信摘要和绑定的原始输入；同一个不可信文件提供自己的摘要不构成authority。这个API不保存完整undo图、补偿历史、历史字典行/索引、完整业务行或发布/ACK状态，不能单独授权事件，也不能标完整跨发布恢复通过。

原冻结73/74一次新事务，无源重放：

- JVM1读取73，选可信baseline对应target generation的raw undo/start，保持XID10.14.2869活跃；`FileChannel.force(true)`后写identity文件并`Runtime.halt(23)`，stdout仅checkpoint摘要，rc23。无commit/事件/目标动作。
- 独立JVM2从该文件和外部摘要恢复，读取连续74；恢复同UID/CID/segment/slot/sequence的undo与end，finish通过。ClosedIdentity的begin record SCN2743312、end2743344、rollback=false，与此前完整native核验一致。原pending文件未改；closed文件保存当前74 header与闭合key。
- 实际状态字节篡改但保留原可信摘要，rc1/零stdout，DIGEST_MISMATCH；截断一字节且提供对应新摘要，rc1/零stdout，NONCANONICAL。单位反例另含source变更、跳序、未闭合、重复end、重复身份、无效SCN/容器/version。

这里是受控JVM硬退出注入，不是主机断电或目录项断电durability证明。探针只核identity，不生产完整事件；真实跨发布产品、完整typed state恢复、checkpoint/ACK故障仍待证。

驱动首失败：Python Path对象列表用str查找导致ValueError，发生在构建/硬退出/独立JVM恢复已通过之后；失败源码及原文保留。仅修驱动，将后续未执行反例补完，未重复已通过构建/退出/恢复轮次，没有改生产代码凑验证。

封存 `lab/out/cross-window-identities-20261007`：[42文件双SHA清单](evidence/DELIVERY-transaction-identity-resume-20261007.json)，写后再次核对；原88+182封存文件全部不变。完整goal active；下一接完整undo/历史字典及绑定raw前缀的持久状态，独立JVM续接后核全部事件，再私有产品真实发布/目标/CPACK。正式C++、一般矩阵、独立目标、评审、部署现场发布仍未过。
