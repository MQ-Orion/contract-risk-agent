"""生成全链路测试用的两份模拟合同（虚构内容，仅用于本地联调/验收）。

用法：python scripts/gen-e2e-contracts.py
输出：data/e2e/contract-a-full-risk.docx、contract-b-missing-clauses.docx
"""

from pathlib import Path

from docx import Document

OUT = Path(__file__).resolve().parent.parent / "data" / "e2e"
OUT.mkdir(parents=True, exist_ok=True)


def build(path: Path, title: str, paragraphs: list[str]) -> None:
    doc = Document()
    doc.add_heading(title, level=1)
    for text in paragraphs:
        doc.add_paragraph(text)
    doc.save(path)
    print(f"generated {path} ({path.stat().st_size} bytes)")


CONTRACT_A = [
    "合同编号：E2E-A-2026-001",
    "甲方（采购方）：虚构甲科技有限公司",
    "乙方（供应方）：虚构乙设备制造有限公司",
    "丙方（担保方）：虚构丙融资担保有限公司",
    "第一条 合同标的",
    "乙方向甲方供应测试设备一批，具体型号与数量以附件清单为准。",
    "第二条 合同金额",
    "本合同总价为人民币捌佰万元整（￥8,000,000.00），含税。",
    "第三条 付款方式",
    "甲方应在收到发票后 120 日内支付全部价款。",
    "第四条 违约责任",
    "乙方违约给甲方造成损失的，乙方应赔偿由此产生的全部损失。",
    "第五条 担保责任",
    "丙方对乙方在本合同项下的全部债务承担无限连带责任。",
    "第六条 保密义务",
    "双方应对本合同内容及履行过程中知悉的信息承担保密义务。",
    "第七条 争议解决",
    "因本合同引起的争议，由被告所在地人民法院管辖。",
    "第八条 免责条款",
    "无论何种原因，甲方均不承担任何赔偿责任。",
    "第九条 不可抗力",
    "不可抗力包括但不限于政策调整、市场变化、供应链中断等情形。",
    "第十条 其他",
    "本合同一式两份，双方各执一份，自双方盖章之日起生效。",
]

CONTRACT_B = [
    "合同编号：E2E-B-2026-002",
    "甲方（采购方）：虚构甲科技有限公司",
    "乙方（供应方）：虚构丁服务有限公司",
    "第一条 服务内容",
    "乙方向甲方提供为期一年的运维服务。",
    "第二条 合同金额",
    "本合同总价为人民币伍拾万元整（￥500,000.00）。",
    "第三条 付款方式",
    "甲方按季度支付服务费。",
    "第四条 违约责任",
    "任何一方违约应赔偿对方损失，赔偿总额不超过合同总价的百分之三十。",
    "第五条 其他",
    "本合同未尽事宜由双方另行协商。",
]


if __name__ == "__main__":
    build(OUT / "contract-a-full-risk.docx", "设备采购合同（模拟，全链路测试 A）", CONTRACT_A)
    build(OUT / "contract-b-missing-clauses.docx", "运维服务合同（模拟，缺失必备条款 B）", CONTRACT_B)
