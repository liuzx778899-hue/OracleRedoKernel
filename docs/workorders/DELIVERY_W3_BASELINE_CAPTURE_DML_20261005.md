# W3 可信基线边界修复：capture 完整 INSERT/DELETE

生产与 lab 提交 bed7fdb；kernel SHA256 `58da93ea85f3246d5a48ae914a29e23aa84bc6ade02de619d01e64e4c51d7932`。[双次 SHA 清单](evidence/DELIVERY-baseline-physical-scope-20261005.json)，原始输入引用[上一轮研究封存](evidence/RESEARCH-full-dml-capture-20261005.json)，证据目录 `lab/out/baseline-physical-scope-20261005`。没有新数据库动作或重放源操作。

原来完整 batch 先对基线之前的 seed 关闭图，遇到无 tail 的合法结束布局而拒绝。现在只在可信文件获取入口重新验证 acquisition SHA、源绑定、SCN 输入覆盖后启用基线语义；没有新增公开 caller-SCN 或 Snapshot 授权入口。所有旧/新 context、原始节点、生命周期和补偿路由都继续完整验证，不按 SCN 删 raw 或删旧 owner。旧事务正常结束且 endRecordScn<=baseline 时标记已被基线覆盖，不宣称其旧物理图已闭合；基线后事务仍完成完整图，begin<=baseline<end 仍拒绝。generic physical API 保持全部图关闭和无 tail 拒绝。

冻结 seq44/45、原获取 snapshot2421228 与 raw leaf50333091，完整 batch rc0/两事件：INSERT10.6.1790 与 DELETE10.19.1789，heap73252/dba50333085/slot1。batch SHA `c0af4ecf1bc77a2bbebd7f481e96c5b7003e372f7724395dcf6c5c9cb2718141`，schema SHA `44edc22399b23aaaf4934ff753454b197824628357670b8e44229e80b8fc94b7`。INSERT before空/after三非空列，DELETE before三列/after空，与独立 native undo 位置、XID、对象、heap位置、值及原 SQL truth 零差异；三比较器 after/slot/operation 反例检出。capture 与原始归档完整行输出一致，最终 seed0/dml-seed/anchor 状态一致。

拒绝/回归证据：

- 同样 raw 的 generic API 仍 `TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE`；实验修改基线使 seed 被选到基线后也拒绝，不补 tail。
- 错 acquisition SHA、实验独立改 source 的获取结果都拒绝；实验重算 SHA 只表示受控反例输入，不把可编辑 digest 当真实获取授权。
- 原真实跨基线输入仍 `BASELINE_CROSSES_TRANSACTION_BOUNDARY`，晚 DELETE before 篡改仍 `ROW_REPLAY_BEFORE_VALUE_MISMATCH`；全部 rc1/stdout0。
- 原 rollback-only 完整图/行仍 rc0/0变化；旧40—42三 UPDATE 的完整 batch 输出逐字节不变（含事件身份、payload、batch/schema摘要）。
- public events 仍 rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。

生产改动后完整内核245项0失败/错误/跳过；新增两边界测试后仅对应类7项通过，不把它写成已执行全套247。首次合成 fixture activation=0 导致两新增项 `NONPOSITIVE_SNAPSHOT_NUMBER`，失败源码/XML/日志保全，修正仅合成头 activation；生产制品不变。首次实际验证编排提前复制旧 jar，SHA仍ea9f58c6…、旧拒绝 rc1原文保全；待构建成功后复制新 `kernel-r2.jar` 才完成上述验证，不冒充新制品失败或成功。

本轮认证的是固定三列非空、可信实际基线、闭合两代 raw capture 的两笔完整 DML。基线前旧图只认证身份/覆盖/路由，不认证 tail关闭；未知旧布局仍可拒绝，生产可信获取/历史字典与一般事务恢复没有完成。NULL/混合操作/多savepoint/跨块扩展、活跃跨窗状态、高压实时、ASM/Windows及新 INSERT/DELETE 到目标 checkpoint 的链路继续待证。没有替换已验收 adapter 的私有依赖或生产部署；完整 StreamHouse pipeline、独立目标实例、现场与发布未通过。

下一步延续[完整 DML capture 矩阵](DISPATCH_W3_FULL_DML_CAPTURE_20261005.md)：独立冻结新 NULL/混合/回滚案例，只执行未验收源轮次，先 raw解析/native/SQL 完整像，再接 target/消费凭证。缺字节或未知布局保留失败，不开启补充日志。
