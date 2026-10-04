# W6 独立Oracle接入模块及严格整批解值

接入模块完整生产树91e675a，内核不变e720013/已通过245测试。本轮只构建新模块并执行其5项针对性测试，0失败/错误/跳过，没有重跑内核接受轮次。独立lab提交见清单。产品共享dirty保持原状，没有部署/目标或源库动作。

## 制品与接口

- Adapter jar SHA256：`79bbd5cfb171ef9d70c29b2307ed8043433568733545bfb4f54f89150f4e5795`。
- Kernel jar SHA256：`15b07ddf4da3f9537b5df8cd996add69e61e6a94b5e92d4e9c7a9c1ed780053f`。
- SPI contract jar SHA256：`93e451fe6c16c665cbdc5b5471a9879cd0a01ec7699a5d9666068dc14c2f8523`，从产品完整提交`67bfb7751d032dc8e47f7599ccb9919a1cff7d58`的7个公开SPI源码只读冻结/编译。不是共享脏树构建，不冒充产品完整reactor。

独立artifact引用内核/SPI，不复制任何redo解析源码。Maven依赖树仅两项compile依赖及JUnit test依赖；所有install-file使用本实验私有Maven仓库，没有替换全局同坐标jar。根内核pom未改。

[接口契约](../interfaces/oracle-spi-projection-v1.md)说明：ServiceLoader发现ORACLE，公开scanner/decoder门禁继续拒绝；限定`OracleBatchProjection.project`在完整batch通过后严格解值所有像，返回实际SPI envelope与Oracle类型化身份/位置sidecar。UNKNOWN LSN/GUID/commitAt不编造，Oracle身份采用oracle1命名空间。该结果不能直接喂SQLServer durable writer或要求commitAt的ODS路径。

## 实际验证

冻结seq40—42/原baseline，无新Oracle DML或配置。三UPDATE经raw capture→内核完整batch→模块严格NUMBER/VARCHAR2转换，三事件与六完整类型化像和原三提交后SQL独立对账，差异0，隐藏NOTE保留；同一新模块重复输出逐字节一致。NUMBER实景只证明精确ID11，非新增完整类型矩阵；相关strict scalar单测证据另列。

四项原raw反例经新模块边界均rc1/stdout0：缺sequence、最后before不符、缺begin、baseline可信摘要错误。对原seq42的最后forward字符值注入非法AL32UTF8字节0x80，保持undo/布局及raw checksum/envelope摘要全闭合：内核完整batch仍实际输出3 raw事件，转换阶段`INVALID_VARCHAR2_ENCODING`、rc1/stdout0。前两条已在内存转换但未对外暴露，证明迟到值错也不会部分输出或目标写入。

真实SPI ServiceLoader取到ORACLE，公开decoder在authorize=true测试选项下仍rc2/0事件/eventsPath=null；scanner拒绝把Oracle sequence冒充IssueSeq冻结窗口。五项模块单测还验证精确NUMBER、SQL NULL/隐藏列、中文严格UTF8、late UTF8/缺列、非空约束/未知类型、公共门禁（组合为5 tests）。两个独立typed比较器反例：漏事件、可解值但错误after，均检出。

首次SPI javac未指定编码，Windows默认GBK与中文源码注释不兼容，编译失败原始命令/完整stderr/rc保留。显式UTF8后只续编该失败步骤及尚未执行的安装/模块构建，不覆盖失败、不重跑已冻结产品输入或内核轮次。

[双次SHA清单](evidence/DELIVERY-oracle-projection-20261005.json)关联三jar、SPI来源归档、完整module树、执行lab源码、5测试XML、依赖树、输入/独立SQL/位置参照、命令/rc/完整输出与晚值反例。大数据和外部SPI源码留忽略的lab/out，不上传产品研究源码或真实样本。

## 接续与六状态

代码交付和新模块针对性验证通过；有限实际SPI binary转换通过，完整产品pipeline未验证。部署、现场验收、发布均未开始/待证。公共decoder和events拒绝不解除，长期goal继续active。

下一步主攻真实产品支持方言Oracle的专属目标DML/幂等/commit凭证及提交后ACK，保留类型化位置，不继续用SQLite实验替代。此目标端实验的技术账本DDL只在专属实验资源，源业务表和源配置不改。并行准备Oracle输入window/位置扩展契约与W5可信schema授权；在这些门禁满足前不接公共发布链。
