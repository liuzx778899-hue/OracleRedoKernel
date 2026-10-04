# W4 实际0x4a开始控制：有限解析交付

运行时3a3a368，Java17新冻结树`lab/out/release-blocking-start/tree`，231测试0失败/错误/跳过，jar SHA256 `0e6a6bac7fca721a10b43b4c04100d5355548275fec95541f147b674b9fb2112`。新增单点probe0287d11不当完整事务入口。完整[封存清单](evidence/DELIVERY-start-4a-20261005.json)关联原始输入/制品/输出及失败，双SHA。

原始seq38 SHA390cb38e…，A begin位置38:14:16实际5.2字段`110000008e060000142b4002080101004a00b000000000000000000000000000`与UID字段bd726528；native独立显示slot17、sequence1678、flags0x004a、siz176、UBA02402b14.0108.01、parent0、PDB UID677737149。同record唯一5.1 begin给出完整XID10.17.1678。实际raw单点解析rc0，XID/UBA/flags/segment DBA与native一致。这里只接受4a且record1/size176/保留零字段/空parent；不能推广任意4a，也不解释flag的通用分配语义。

五份重封seq38改unknown flags4b、record1→2、size176→177、非零parent、UBA DBA首字节，scan全部rc0；单点前四项UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT，最后MISSING_TRANSACTION_BEGIN_UBA，均rc1/stdout0。新两测试以真实字段验证begin精确匹配、错误UBA与布局拒绝；同件jar旧seq30 INSERT/DELETE、seq31完整回滚后UPDATE、seq32部分回滚三完整行native/SQL零差异，seq28/29真实基线跨事务拒绝。未重复源DML。

## 新发现与接续

原seq38/39在开始控制放开后仍UNDISCOVERED_OBJECT_TRANSACTION_COMPENSATION，不能当并发拒绝通过。新的只读导出脚本14cebb5绑定专属容器、复制seq37及全范围37—39 dump，无DML/config/切换。seq37 SHA `02a4e097c7f3f9d02015b78bae0c5358b4462fd29add56ec4b5dd94ba7532131`；全dump SHA `3a72493797d249b5b8bbac5743aefec7cdcf92d8248405dcf8aa63762fc206f0`。前次失败实验A的事务跨入后续归档，bounded SCN dump没有完整说明所有原始输入；必须保留真实归档及前缀，不删除补偿来凑正例。

补齐连续seq37/38/39后physical/rows均UNSUPPORTED_HEAP_OPCODE_OR_FIELDS、rc1/stdout0。完整native显示B XID10.22.1675在SELECT FOR UPDATE获得锁时产生LKR，forward及inverse均出现锁行操作，随后才URP。当前HeapRowPatch只支持IRP/DRP/URP，所以同一行阻塞物理/完整行仍未通过。这是后续实际格式门禁，不能把锁记录忽略或伪装业务UPDATE。

下一步只读分析冻结LKR forward/inverse字段、UBA、XID、before lock元数据及完整分配图，独立native验证节点覆盖；保持锁节点在物理图但不产生值变更事件，必须以真实字段与拒绝证明，再验三业务UPDATE完整行和各SQL时点。原实验两笔已提交，严禁重放，源fixture现为ID11/blocking-closed/new-note。持续capture/ACK、接入、生产信任/历史字典/恢复、现场与发布仍未完成。
