# W7 真实独立空capture → 空目标提交 → checkpoint/ACK

合法独立空batch有限通过，不再以49—50缺覆盖基线后缀替代。原49—50拒绝保留，旧44—50 DML与目标接受轮次不重放。源driver f658a9a，源码/制品/失败原文/输入输出双次SHA及命令rc见[清单](evidence/DELIVERY-empty-capture-target-20261005.json)。长期目标、产品/现场/发布未完成。

## 新专属源范围

专属oraredo-19c-lab-20261004 / ORKPDB，真实新表ORKLAB.T_DML_EMPTY_20261005 heap73302/index73303。仅新表先一次allocation INSERT0→DELETE0→COMMIT以分配段，随后实际只读基线SCN2429909、rows0、SHA b4857b5d…；基线后只执行一次INSERT11/empty-undo/empty-note→ROLLBACK，XID4.11.607，SQL步骤1行/结束0行。实验DDL/DML不当生产零侵入证明。

真实collector采到seq51共3873块，journal29dfcac021b78b27aaa238576fe057a198b52d25214acf5123a88c0e9b025e3f，与归档数据块零差异，next52，collector终止。源supplemental NO/NO、原表11/blocking-closed/new-note保留。不会重放本轮操作。

## 发现的控制归属缺口及修复

旧a084内核完整batch rc1/stdout0，UNSUPPORTED_PHYSICAL_NONBEGIN_SLOT_CONTROL。实际pre-baseline allocation事务2.0.601窗口内，有同segment外来2.4.601 begin，obj/data720；rollback窗口另有外来10.0.1813 begin。四条raw控制SCN/slot/sequence/flags/UBA及精确同record begin undo与独立native dump零差异。

生产代码仅允许精确同record UBA绑定、身份非目标、logical/data对象全部不与目标角色相交、唯一控制的外来begin；活动segment/slot冲突仍拒绝，包括同sequence不同USN，不把外来事务纳入目标history或发布事件。没有忽略未识别的非begin、advance/head控制。新增正例及缺undo/slot冲突/重复控制/角色相交反例。

首轮新增合成测试把record写过512字节边界，BLOCK_IDENTITY_MISMATCH失败原文及源码保留；仅修fixture布局后11针对性通过。继续补同sequence/different-USN保护后生成独立r2制品，254内核测试/15模块测试0失败/错误/跳过；中间制品6329…及其日志保留，不当最终件。最终kernel960954207b626be4d19db547ce2a9e3c5245075e84edb7196935ba3c0489c55e，adaptercab860096d038b5639e293a91a04324885e09f5e32526a4027b2acbe0359c534，私仓不可变版本0.1.0-bounded-foreign-begin-r2，SPI93e451fe…不变。

冻结seq51仅补旧失败完整batch：rc0/事件0，batchd809117450a53017c96453e165ccaf901e6a4d58bbf0bead71353be7eec86a8a。raw自主完整两图6原始/2补偿/4保留与native零差异；pre-baseline allocation图4/0/4由实际获取覆盖，post-baseline rollback为2/2/0。完整state为空。新制品对旧UPDATE/非空/NULL/混合完整batch四份输出逐字节不变；新代码对应回归，不重跑旧源实验/崩溃矩阵。

## 空目标与恢复拒绝

仅专属目标ORKTGT_CMT_20261005新EMPT五表：真实空batch投影entries0；业务行/审计/成员/commit凭证实际0/0/0/1，凭证eventsCount0。目标提交之后checkpoint前沿51/3873/endExclusive2429934及ACK发布。三次正常/重复/恢复消费新增均0，文件原字节稳定。

已发布状态下删除本batch实际目标commit凭证：消费者rc1/stdout0、ORACLE_TARGET_ACK_WITHOUT_COMMIT，目标0/0/0/0，不补写commit，不更改checkpoint/ACK。恢复该自有metadata凭证后消费0，完整SQL回原健康状态。无源业务动作/配置变化，helper终止。没有新SIGKILL/断电证明。

目标预检首轮Python转义把SQL escape写为空，ORA-01425/rc1及失败脚本保全，尚未创建目标。改用substr后只续失败及未执行步骤，已接受raw/native/状态探针不重跑。

独立比较器从冻结raw batch/capture collector元数据重构空目标count/receipt、checkpoint全部source/schema/baseline/leaf/capture/位置字段和ACK，零差异；错误eventsCount、重算checksum但错frontier、错ACK count三派生比较反例检出，原证据不改。三重封raw（外来UBA错、同sequence slot/undo身份错、缺rollback marker）结构均rc0，完整batch均rc1/stdout0；实际原因MISSING_TRANSACTION_BEGIN_UBA / UNDO_INVENTORY_ADDRESS_OR_SLOT_MISMATCH / MISSING_INDEX_LEAF_INVENTORY_MARKER。新public decoder仍拒绝。

## 下一主线与产品发现

只读核StreamHouse HEAD67bfb775…及实际源码SHA；共享dirty未动。PublishGateService仍要求v2事件ID、非空server/database、SQL LSN，并通过List.of构造LSN列表；Oracle null LSN会在该代码路径触发空指针异常。INSERT/DELETE公开gate要求不存在侧为null，而当前私有adapter为empty map；C++main现路径是目录sweep和*.ldfcap尾件，仅engine_type字符串不能证明Oracle真实采集。这些都是下一产品门禁，不能用本轮私有target通过替代。

接续[产品接口执行包](DISPATCH_W6_PRODUCT_GATE_CONTRACT_20261005.md)：保持Oracle身份/位置真实，先闭合公开契约及可信输入注册，不以改前缀、伪LSN、字符串授权或静默跳过gate凑通过。活跃跨窗、生产历史schema/基线、任意事务/类型、C++实时/Windows/ASM/高压/断电/独立目标/现场/发布待证。
