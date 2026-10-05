# V2新raw实际产品提交与重启核验

产品完整代码efd068f776fd69641c2629d1b73e9c0f09cdc3f7、文档b2a754d，草稿PR#13继续依赖#12、独立评审PENDING。原仓lab驱动3f61c4d；原内核391eaf7/271测试不重跑。产品新完整提交树`clean verify`737/0/0/0；application SHA `2db7106fecbb443c92b62a29e41e12dc74d71cdd099df30ad0af49b25b0f6d90`，包内私有kernel672f01bc及adapter9516d4f9精确SHA核验。

## 本轮通过范围

- 实际acquisitionId100634385207877632/SCN2517052，六SYS锚点、三历史列和基线seed行位置/值字节独立固定SCN查询零差异。NO/NO、旧表11/blocking-closed/new-note及新源seed保持；只读补核验，无源动作重放。
- 保留旧444 helper后仅替换专属服务为新application，原获取登记全字段/基线字节完全一致。新目标前缀V2DML先只读确认0表，再创建专属五表；targetId严格沿runtime为oracle-dml-V2DML-20261005。
- 绑定实际获取ID，baselinePath为空，由服务恢复受信基线；seq62—64、完整source/schema/evidence、envelope摘要及consumer=v2-consumer绑定。ACQUIRED_ONLY本身不授权发布，不走客户端options放行。
- 错authority实际认证HTTP提交任务100642375008870400，FAILED/REFUSED/event0；目标applied/member/commit/ledger0、checkpoint空、窗口PENDING、ACK无文件。恢复authority及同task后DONE、批次PUBLISHED/3、窗口CONSUMED。
- 三事件完整INSERT/UPDATE/DELETE中间像（TXT NULL→v2-after，隐藏NOTE NULL）、顺序成员/唯一target commit、完整control checkpoint与持久ACK字段独立零差异。两回滚0业务事件，不能以最终seed行相等代替过程。
- 重复及同件服务重启后重复零新增，目标/控制核验及ACK字节一致，acquisition全字段/基线仍一致。operation/checkpoint-source/ACK三个比较反例检出；ACK SHA `fbc22f214e7fe1b609f5f81e15511fbd05948daa640c69f66c4c26d770639a7d`。

## 保全与边界

[446文件双次SHA清单](evidence/DELIVERY-v2-dml-product-runtime-20261006.json)，原197封存文件逐项未变。旧解析/collector原失败保留；新私有坐标安装r1因PowerShell未引用`-Dfile`截掉扩展名而失败，r2正确引用成功，原日志/rc均保留。实际构建、HTTP、只读SQL命令/rc和执行pin关联；驱动源码另为执行后留存，不冒充所有脚本执行前封存。

専属helper ork-oracle-v2-dml-20261006正常停止/exited143，旧444容器另名保留，源Oracle保持running，不重启源。控制ORKV2DML_20261006及目标V2DML保留，未操作共享checkout或旧目标。目标同专属Oracle实例跨schema，非独立生产实例。公开decoder继续拒绝；部署生产未开始，现场待证，发布未开始。

## 未完成主线

接续[同候选历史输入产品拒绝派单](DISPATCH_W5_V2_HISTORICAL_PRODUCT_20261006.md)。V2本轮静态完整DML和产品链已通过；历史schema raw/marker擦除产品门禁、COL$间接完整链、活跃跨窗口、持续C++采集/租约、独立目标、版本/类型/存储/压力断电、独立评审/现场发布仍未完成。保持完整goal和automation；Codex卡顿排查按用户新指令放在全部Oracle目标完成之后，不抢主线。
