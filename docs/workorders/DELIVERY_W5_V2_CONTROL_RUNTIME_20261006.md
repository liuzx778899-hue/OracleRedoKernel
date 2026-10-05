# W5 V2产品获取账与真实HTTP恢复

产品代码ea1975c66064a3dc8a49c779ca70f8300abcb5e2，文档5f57c56，隔离分支codex/oracle-dictionary-acquisition；[草稿PR#13](https://github.com/liuzx778899-hue/StreamHouse/pull/13)依赖#12，已附属本chat，未合并。共享dirty checkout未修改。

## 已交付
新获取调用opaque acquireDictionaryBound，必须带字典锚点；metadata包含完整anchors，恢复重新严格导入并与获取账比较。旧V1有限兼容保持，不能由此获得一般历史权限。新私有坐标kernel0.1.0-dictionary-profiles-444630a4、adapter0.1.0-dictionary-snapshot-9516d4f9；正式候选从完整提交树clean verify，737 tests全部0failure/error/skipped，应用内嵌两jar SHA与先前原制品一致。没有重新宣称adapter21或旧kernel269本轮重跑。

## 实际验证
- 新专属控制schema ORKV2CTL_20261006、新helper/loopback37221。实际认证HTTP acquisitionId100631208685281280、SCN2514178、6锚点，snapshotSHA ba78678efa5f9a5141bb5d9163b2a2504fa2ac269a8dfd3ee10dd2f034f4dccf。
- 独立SYS ASOF同SCN角色位置/历史列与产品文件零差异。仅做独立尺子，事件仍不从SQL生产。
- 实际损坏快照、删账metadata中的anchors拒绝；恢复文件与账后再次读取成功；停止/启动同服务，持久行所有字段和文件字节一致。
- 首轮比较器将新插入内存时间纳秒与Oracle持久微秒时间直接比较而失败，原源码/失败日志保留。身份/证据/状态字段先逐一一致，再以首次持久GET（含原持久时间）核验恢复/重启；没有忽略恢复后的时间漂移。
- 状态ACQUIRED_ONLY，bindings0/event ledger0，无新raw消费/目标提交/CPACK。0源业务DDL/DML/配置动作，NO/NO及旧表11/blocking-closed/new-note保持。只新建专属控制schema；helper最终exited143，未重启源容器。

## 保全与未验收
[109文件双SHA清单](evidence/DELIVERY-v2-control-runtime-20261006.json)，secret/private SQL不入哈希或Git；源码执行前pin与执行后源码副本/完整树build时点分别记录。实际运行同完整候选，未换装生产。

实际新raw到target/control/checkpoint/ACK、marker缺失产品HTTP目标前拒绝、SYS间接依赖/完整历史覆盖、活跃跨窗、C++/采集租约/独立目标/版本存储类型/压力断电/评审/现场/发布未完成。现有fixture和seq59/60源事务关闭，不能重复旧动作；本轮获取SCN晚于旧raw，禁止回填旧窗口。

[下一单](DISPATCH_W5_V2_NEW_RAW_RUNTIME_20261006.md)，P0整体未闭合，goal active，automation保留。
