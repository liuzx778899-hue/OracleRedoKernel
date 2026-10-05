# W3 可信完整行的 nullable 省略尾列

生产/lab4f79691，kernel SHA256 `a084218e5ebc9c4e3313cd84beba0c2bea4ff280a82f93fcf5890d2a0493f968`，完整251测试0失败/错误/跳过。[双次SHA清单](evidence/DELIVERY-nullable-tail-20261005.json)，输入沿用[NULL样本封存](evidence/RESEARCH-null-dml-capture-20261005.json)，输出目录 `lab/out/nullable-tail-20261005`。没有新源/目标数据库动作，不重放INSERT/DELETE。

仅可信获取入口把实际验证的physical column metadata带入行重放；普通公开Baseline只有行值，不获得nullable补尾权限。完整F/L INSERT以及DELETE的INSERT inverse按storedColumns核完整前缀，前缀缺列拒绝；只对schema验证为nullable且位于省略尾部的ABSENT组成NULL。physical patch仍是ABSENT，HeapRowPatch/UndoRowPatch/HeapUndoPair class与上轮制品逐字节一致。DELETE inverse完整像与当前行严格比较；UPDATE的ABSENT保持沿用旧值语义。

冻结seq46/47、snapshot2423979、leaf50333107，完整batch rc0/两事件：INSERT11/NULL/NULL，DELETE相同完整before；native raw仅cc1/col0 c10c，与原SQL三列完整值、对象/XID/undo位置/heap位置零差异。batch SHA `270a502c1b194aa51a565c3e4628804ace20a89eacc7dfb0bad9c14e1b8d2bda`；schema SHA `24f2f7d26ef533d67cf1d130d9a3122e8a5b4c89bb62283c06784587edeb434f`。capture/archive完整行一致，最终seed0/null-seed/anchor一致。

四实际入口反例rc1/stdout0：普通Baseline、实验可信输入NOTE改非nullable、修改snapshot但沿用原获取SHA、重封capture中的晚DELETE inverse ID从11改12。最后一项通过capture结构/物理配对后由完整before拒绝，不能先泄漏INSERT事件。合成测试另覆盖中间ABSENT及不匹配extent。三独立输出比较反例fake VALUE/ABSENT/错误操作均检出。旧非空两DML与旧三UPDATE完整batch逐字节不变；public events继续拒绝。

有限三列固定schema、完整F/L单rowpiece、两事务闭合capture的尾部NULL通过，不认证中间NULL/多rowpiece/历史DDL或一般DML矩阵；可信SHA仍依赖独立获取授权，不把重算checksum当真实性。此次未替换adapter私有kernel、未消费新NULL事件到target、未部署产品。下一步[完整DML矩阵](DISPATCH_W3_FULL_DML_CAPTURE_20261005.md)中的同事务INSERT→UPDATE→DELETE、INSERT/DELETE rollback，再新完整DML到target/checkpoint；公开decoder/完整产品/独立目标实例/现场/发布未通过。
