# LOB 历史变化与恢复续接

本包打通 BasicFile/SecureFile 的 ADD→DROP 普通尾列正常路径：各两条完整 UPDATE，完整前后值差异 0。原 raw117/118 复用，旧源动作未重放。独立两进程字典 checkpoint 恢复与直接解析的事件和批摘要完全一致，篡改状态被拒绝。不是整个 LOB 历史功能收尾。

修复范围：LOB 的 COL$ 类型/property；逐记录/提交历史列宽与 NULL 默认；真实 DSC 删列的完整所属事务、字典变化和每个被删旧值覆盖；LOB 定位器保持产生时的列宽；实际 ITL2 锁、单尾 NULL 物化及维护提交布局。SYS 事务只有完成逐项依赖核验才从业务/维护分类中排除。DDL pending 状态尚不支持 DSC，明确拒绝，不丢失依赖后恢复。

验证：tests-close-r1 rc0，43 项中执行39/跳过4，失败/错误0；冻结样本另走完整入口，不用条件跳过冒充实测。package-r1 rc0，内核 SHA256 `26284ca98a386c66ad516951f16adef956c26cc0ff551596410f2a51b434471c`。未换产品依赖、未部署。

证据入口 `lab/out/lob-history-20261011/offline-r1`：`add-proof-r1.json`、`drop-proof-r1.json`、B/S-enroll-r1、B/S-resume-r1。raw117 `5d2d4de1245a232d8a8538cc8c531d79630444bdb642f9b6e80d4c281b82efb7`；raw118 `73789d725587f796b5e3dc10eaaf4a50c3381766cdc046d21ddad46b1053dfa8`。两种 DROP envelope 分别 `e4e6efa403428f25f3a561b389b39a2f8368508c4b6ec1701b0b9087850e13ff`、`a6bb31346d0f4f7781ab5cd974992e1bdd817ea9d8e6850df03e0207f37fd05d`。恢复批摘要分别 `303fd586bba63e6762de38145ee95b0304bbb08036cd024e640ccf52d3c5361c`、`acd3a1877ecd18cf449fc44aad9ee21a1b9c462a22f7fb6ee4939afd5655c26a`。

用户新增存储迁移必验范围已执行一次专属 fixture 的 BasicFile→SecureFile 在线重定义，之后一次普通 UPDATE；源端迁移前值保持，迁移后独立 SQL 结果为 ID1/after-migration/00AAFF80/after-migration-text。逻辑 heap/index 身份从81014/81019变为81024/81035，不能按静态对象绑定读未来日志。原始120已冻结：8015360字节、SHA256 `6a2c6677ab29818dba14663850ca9cc56f0ba7564eec9227d0d9f387c8cb2f36`；迁移前可信获取 `72555e21d09c917f33e2b2d7a95c34269cb11b3d16c738f7008d91979b2028d9`。获取参数错误、NOT NULL 复制冲突及停止期间只读状态拒绝均保留；未重建 fixture、未重复 START_REDEF_TABLE 或业务 DML，续接既有重定义完成 COPY/FINISH。source-run-r4 rc0/source-stop-r4 rc0，库已停，Desktop停止；数据库/卷保留。

迁移实验路径参考 [Oracle 19c 官方 LOB 存储文档](https://docs.oracle.com/en/database/oracle/oracle-database/19/adlob/using-oracle-LOBs-storage.html)。SQL、native dump 和获取结果仅为比较尺子；事件生产仍只解析原始 redo。

后续直接接冻结 raw119/120：TRUNCATE 当前拒绝 `NON_ROW_FIRST_ROOT_IDENTITY_OR_LINK_MISMATCH`；迁移拒绝 `UNSUPPORTED_DICTIONARY_DISCOVERY_ROW`。保留原失败，不当成功。对象代际/重建/迁移解析、统一生产 checkpoint 与值原点恢复、readCut 安全回收、产品新链仍须补完；ARRAY 任意节点部分行及真实同SCN两窗继续。原五项未全完，不合并/部署未完成能力；D5 按用户要求搁置，TDE 不启动。

2026-10-11 迁移解析续接：不重做迁移，复用 raw120。已补三类实际字节前置：SYS TAB$ cluster 空间扩展仅在完整同记录前后配对后排除字典行变化；重定义的 24.1 多对象数组要求确切数量、唯一对象及配套标记；OBJ$ 五列 UPDATE、25/26列 INSERT 与 inverse DELETE 按当前 UBA/完整 XID 绑定，37列布局仅在省略尾部可空且没有存储值时补 NULL。两条安全的真实 SYS 元数据记录已作为小型测试资源保存，来源为 raw120 RBA9741/16、9783/16；不是业务行数据。

本次相关9类共68项，失败/错误/跳过0（tests-close-r1 加最终受影响类复查，避免重跑全部）；BasicFile/SecureFile 原 ADD→DROP 完整入口各2事件且 envelope 摘要与前包完全一致。package-r2 rc0，最终内核 SHA256 `b6bb1a25815b35c08ea6a797fc8a5363c269c7db6690e023affeac35f75b94aa`，未更换产品依赖、未部署。证据位于 `lab/out/lob-migration-20261011`：tests-close-r1、tests-insert25-r1、tests-insert-boundary-r1、B/S-drop-regression-r1、package-r2。

完整迁移入口仍未通过：migration-r5 rc1，原文 `UNKNOWN_OR_CONFLICTING_DICTIONARY_DEPENDENCY:Position[archiveSequence=120, block=9855, offset=176, vector=1]:36:36`。这笔重定义事务包含当前基线未覆盖的 SYS 对象索引及新对象依赖，不能绕过后发布。接下来先补完整所属事务的字典依赖，再连接迁移后的 heap/PK/LOB 对象、值原点及恢复回收；TRUNCATE 根记录缺口仍保留。前述首个迁移失败为历史时点，此处为最新失败。用户再次明确 D5 搁置，RAC/ASM 不作为本轮阻塞、不自动恢复，库/helper/Desktop 均保持停止。
