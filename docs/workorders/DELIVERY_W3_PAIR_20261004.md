# W3 精确行/undo配对交付

源码`3e1b5bc5bf562455c9e5540aa3ad0e9663be079d`，main。完整提交树独立构建57项测试0失败0跳过；jar SHA256 `baad88ceb363af04ea62488b339c3860dd9a1f87047b0a69293a46e5d9368fcc`。[封存清单](evidence/DELIVERY-3e1b5bc.json)绑定原始输入、独立dump与正反例输出。本轮没有新Oracle DML、部署或目标写入。

## 配对规则与实证

`HeapUndoPair.bind(record,row,columns)`只接受当前实证xtype1正向行变化，以同CON_ID、精确UBA(DBA/sequence/record)找到同record唯一5.1逆向片段。要求正向也唯一使用该UBA，DBA/slot相同、操作互为逆向；若正向带完整XID必须与undo一致；UPDATE的出现列集合须一致。逆向值允许与新值不同，未出现的键/列保持ABSENT。

本轮不使用邻接或vector顺序、不从恢复后的UBA猜原操作。匹配失败、多义、错位置、错身份、错改列集合直接拒绝。source/SCN字典、活跃事务归属和补偿链仍是另一个集成门禁，不能只凭物理pair发布事件。

同件jar对19c seq8/9目标dataObject73024：15+3共18对，正向出现列39+9=48、逆向出现列9+1=10，与独立Oracle dump零差异。覆盖物理块0x03000083和0x03000087，包括跨归档6.12.579的UPDATE和9.19.579的第二块INSERT。旧W3正向对账只有第一个DBA；此处补齐全部选定目标undo的正向对应，不代表全Oracle类型支持。

研究驱动以目标undo定位候选物理行；每个目标undo必须有pair，计数0或未配齐即失败；整份归档配对完成后才输出研究结果。它仍不输出CDC事件。

## 拒绝证据

新增6项单元测试覆盖顺序反转、缺失/重复undo、重复row、slot/DBA/XID/column-set错配、不同CON_ID、错误undo sequence、外来record和XR恢复后指针。

同件jar还读取真实归档8的record30679/360，先证明原seed配对成功，再生成六项内存派生反例：缺undo、重复undo、重复row、逆向slot篡改、正向XID篡改、xtype改为XR，全部返回预期拒绝原因。harness本身rc0表示6/6测试通过，stdout是审计结果，不是事件；这些派生输入不冒充六项新Oracle执行案例，也不是文件checksum反例。

## 六列与后续

代码交付、针对性物理配对验证通过；完整行/事务集成进行中；部署和发布未开始；现场验收待证。`events`仍rc1、CDC事件0。

下一步从18对原操作建立undo历史，将5.6/5.11与header/活跃身份/原逆向行变化交叉匹配，验证T007仅撤销discard UPDATE和INSERT8而保留INSERT7，并验证旧ITL、重复候选及缺链安全拒绝。之后继续SCN字典与完整行基线。
