# ARRAY 与时间类型产品接线

在最新产品 main 基线 dc593c6 的隔离工作树接线固定二进制，候选产品提交19071ed。首次完整后端构建862项测试失败0；应用SHA256 `7b515f3880a0e8601dae93613bc8cb7139521ca5891ec86d0d09dac08fe1a083`。

ARRAY：新认证HTTP基线后仅一次INSERT SELECT20，原始114解析20唯一完整事件，经实际产品任务写入目标。applied/member/commit/ledger=20/20/1/20，源目标完整行差异0、额外0、缺失0，窗口CONSUMED、任务DONE，实际checkpoint/ACK同字节。ACK SHA256 `8eec329743c855d9b08a794944bea8b26d3029e058a1e895967c8f7997ec560d`。证据`lab/out/array-temporal-product-20261011/runtime-r1/A-proof.json`。

时间：新认证HTTP基线SCN3255737后一次UPDATE三行，覆盖DATE/精度0、3、6、9/TSTZ/LTZ/44BC与1BC。原始115已冻结；首轮因九列packed suffix拒绝，原失败保留。补齐实际stored9/count8连续后缀及三行长度对齐标记3，仍拒绝未知映射、截断、多余值和未知对齐。相关25项失败0；冻结115完整入口输出3完整UPDATE，全部九列前后像保留。最终内核SHA256 `86cb737b55bcffe9c56b8f9d0f709b04b04dc0a583b41a75508958552d253789`。事件来自原始redo，SQL仅作独立核验。

时间产品目标/checkpoint/ACK正在续接，不重放源动作；不能以离线解析当作产品通过。源与helper停止、Desktop停止。新内核产品固定坐标更新与同件构建随后执行；其余同SCN真实恢复/发布、LOB历史/恢复/回收及未证明ARRAY撤销布局继续，五项未全完。D5搁置，模拟生产尚未开始；最终合并与重新编译部署单待五项收尾。
