# W7 journal原始块→Java record/vector

生产内核cba8bbc，独立lab比较器da24a3c。研发jar SHA256 `cf8f48984dbff4447f43a4072d5e2af7eb30d54023482f34e6e57d5ea749178f`；Java17 clean verify 237测试，失败/错误/跳过均0。保留build全文、测试XML及完整提交树zip。

## 实现与边界

`CaptureJournal.read(path, expectedSha256)`接收实际collector receipt的可信SHA，先读入并冻结journal字节；验证整件SHA、ORACAP01帧长度/摘要、canonical metadata、唯一有序0..NAB-1块、sample顺序、source一致、payload摘要、raw checksum/index/sequence及真实关闭头。只支持此次collector的严格canonical closed-generation profile；OBSERVED_CHANGED、重复版本、缺块、active NAB或非支持profile拒绝。SHA是控制进程完整性边界，不认证外部签发者。

`Verified`保留原始在线容量和关闭范围，block读取返回副本。没有写中间归档，没有改block0容量、block1 NAB/SCN或重封原始头。`RedoRecords`将原FileChannel读块换成一个共用读块游标：归档入口继续从原FileChannel读，capture入口从已验证journal读。`readCaptured`只返回完整不可变list；晚record损坏时不返回已解析前缀。既有vector field数组API保持原状，不宣传整个解析对象深度不可变。

## 真实输入与独立对账

复用已提交一次源实验的真实seq40/41/42 journal及独立冻结archive；本轮只有专属diagnostic dump，没有DML、配置、切换或源动作重放。生产解析不读取dump/SQL/native元信息。

- 三件capture解析19,516 records、36,124 vectors。record/vector文本（包括所有字段bytes）与对应冻结归档逐字节一致。
- 新独立完整Oracle native dump覆盖所有19,516 record头：位置/长度/VLD/SCN/subSCN/CON_UID/opcodes零差异，没有多输出、漏输出或未识别头；这是结构对账，不是事务/DML支持证明。
- 实际9,023条record跨512-byte块边界：seq40为9016、seq41为3、seq42为4；原游标在journal块上完整重组。不是跨sequence未关闭record流验证。
- 之前已验收解析jar db949a…在同三冻结archive上的所有vector bytes与新capture入口完全一致，归档入口提取未改变真实字段解析。
- 严格比较器四反例（漏末行、重复行、晚SCN错、乱序）全部rc1；最终比较器再对三份已有native/record输出核对rc0，没有重复dump或源动作。

## 拒绝证据

八项capture反例均rc1/stdout0：截断末帧、删除完整末帧、重复帧、envelope损坏、OBSERVED_CHANGED、payload与frame/receipt重新计算后raw checksum损坏、最后实际record VLD改0并重封raw checksum/frame/receipt、错误可信receipt SHA。最后record破坏发生在一批已有合法记录之后，拒绝没有部分输出。派生副本重新授权receipt只用于检验内层结构门禁，不能当作篡改签发者认证。

实际public `events`仍DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED rc1/stdout0。结构解析/API list不是CDC事件出口，也不是目标ACK。

[双次SHA清单](evidence/DELIVERY-capture-records-20261005.json)关联制品、完整源码、237测试、原始journal/archive、native dump、全部stdout/stderr/命令/rc、反例输入及比较器反例。真实大制品在lab/out，Git只安全摘要/代码/脚本。

## 接续

有限关闭journal→完整record/vector结构集成通过；实时未关闭record、跨sequence恢复、多member collector续接、物理事务/字典/完整行在capture入口、消费位置/目标ACK/StreamHouse接入仍待证。readAllBytes及缓冲list尚非无限流/高吞吐设计。无Windows/ASM/NOARCHIVELOG或现场/发布验收。

下一步把已验证capture输入接入同源事务/字典/基线全批重建，复用本次已执行三UPDATE及baseline，不重放源动作；再落实capture/消费者/目标ACK三个独立持久位置及W6。
