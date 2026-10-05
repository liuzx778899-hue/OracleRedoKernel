# W5 获取账关联新 DML 执行包

前置：产品996cfd5，完整提交树735测试通过。新批准输入acquisitionId在消费和发布核验时关联真实获取账；路径只能等于服务端真实路径，source/SCN/hash/对象/物理列不匹配拒绝。旧有界批准冻结输入保持兼容，不扩大生产批准范围。代码/API正反例不是实际新DML验收。

环境只限oraredo-19c-lab-20261004及专属新控制ORKACDML_20261005，helper ork-oracle-acquired-dml-20261005、loopback37219。新fixture ORKLAB.T_ACQUIRED_DML_20261005 / PK_ACQUIRED_DML_20261005；旧表及旧闭合fixtures不复用。源业务仅一次create/seed0/acquired-seed/anchor，再三个事务：INSERT11→UPDATE TXT→DELETE11提交；INSERT12全回滚；DELETE seed0全回滚。独立保留每步SQL可见完整像、XID、实际archive/capture范围和dump验证尺子；dump/SQL真值不生成事件。

唯一动作纪律：脚本prepare必须新建目录、对象/用户不存在；capture必须无执行标记，先封存实际源码/制品/获取账，再置不可重放标记。任何失败先保全rc/stdout/stderr和当前数据库/collector进程，禁止重跑提交动作。采集观察超时不等于进程结束，只检查同句柄后续接。

先实际认证HTTP固定SCN获取到ACQUIRED_ONLY，源身份8字段与生产配置相符、内核snapshot严格导入。再从同baseline覆盖起点跟踪关闭member原始字节，完整闭合SCN链、raw leaf唯一性与事务/完整像均独立核验。未知结构、安全拒绝或最终seed相等都不当3事件通过。

通过后再专属新目标ACDML与产品控制任务：受控批准输入绑定actual acquisitionId（普通options不可授权），完整目标中间像/成员/commit→控制台账/checkpoint→ACK，重复零新增，坏获取账在目标前拒绝。受控独占fixture的无DDL假设仅为有限实验前提，不充当历史DDL/ABA或生产零配置证明。三库新增迁移、历史字典、跨窗/C++/独立目标/现场发布仍待证，评审PENDING。
