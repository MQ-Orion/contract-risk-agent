# -*- coding: utf-8 -*-
"""生成 eval/samples 下的三份虚构脱敏合同样例，并就地完成自检。

只使用 python-docx，不安装任何第三方包。

用法：
    python gen_samples.py

输出：
    ../samples/purchase_contract.docx  设备采购合同（中文条序号 + 表格 + 超长条款）
    ../samples/service_contract.docx   技术服务合同（阿拉伯数字多级编号）
    ../samples/nda_contract.docx       保密协议（中文括号序号）

说明：全部主体、金额、地址、联系方式均为虚构，不引用任何真实合同文本。
条款编号一律写成普通段落文本行首，不使用 Word 自动编号（numPr），
以保证解析出的纯文本行以「第十三条」「1.1」「（一）」这类标记开头。
"""

from __future__ import annotations

import os
import re
import sys

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

SAMPLES_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir, "samples"))

# 与 doc-module ClauseSplitter 的三类标记正则保持一致，样例必须能被它们命中。
CN_MARKER = re.compile(r"^第[一二三四五六七八九十百千零〇两0-9]{1,10}条")
ARABIC_MARKER = re.compile(r"^\d{1,3}(?:\.\d{1,3}){0,3}(?=[\s、.．:：]|$)")
PAREN_MARKER = re.compile(r"^[（(][一二三四五六七八九十]{1,3}[）)]")

# 任务书给定的校验正则（与解析器正则不必完全等价，仅用于回归统计）。
CHECK_CN = re.compile(r"^第[一二三四五六七八九十]+条")
CHECK_ARABIC = re.compile(r"^\d{1,2}\.\d{1,2}")
CHECK_PAREN = re.compile(r"^[（(][一二三四五六七八九十]+[）)]")

HANZI = re.compile(r"[\u4e00-\u9fff]")

BODY_FONT = "宋体"
TITLE_FONT = "黑体"


# --------------------------------------------------------------------------
# 排版辅助
# --------------------------------------------------------------------------
def setup_styles(document: Document) -> None:
    """统一正文与标题字体，并显式声明东亚字体，避免中文回退成西文字体。"""
    normal = document.styles["Normal"]
    normal.font.name = BODY_FONT
    normal.font.size = Pt(10.5)
    normal.element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), BODY_FONT)
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(4)
    normal.paragraph_format.line_spacing = 1.35


def _apply_run_font(run, font_name: str, size: float, bold: bool) -> None:
    run.font.name = font_name
    run.font.size = Pt(size)
    run.bold = bold
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), font_name)


def add_title(document: Document, text: str) -> None:
    p = document.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(10)
    _apply_run_font(p.add_run(text), TITLE_FONT, 16, True)


def add_meta(document: Document, text: str, bold_label: str | None = None) -> None:
    """合同首部的编号、当事人、日期等信息行，普通段落、无首行缩进。"""
    p = document.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    if bold_label:
        _apply_run_font(p.add_run(bold_label), BODY_FONT, 10.5, True)
    _apply_run_font(p.add_run(text), BODY_FONT, 10.5, False)


def add_heading(document: Document, text: str) -> None:
    """条款标题行：编号写在文本行首，加粗，不使用自动编号。"""
    p = document.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(0)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    _apply_run_font(p.add_run(text), BODY_FONT, 10.5, True)


def add_body(document: Document, text: str) -> None:
    """条款正文段：首行缩进两字。"""
    p = document.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(21)
    p.paragraph_format.space_after = Pt(4)
    _apply_run_font(p.add_run(text), BODY_FONT, 10.5, False)


def add_note(document: Document, text: str) -> None:
    """无编号的说明段（如「设备清单如下：」「鉴于」），正文样式。"""
    p = document.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(21)
    p.paragraph_format.space_after = Pt(4)
    _apply_run_font(p.add_run(text), BODY_FONT, 10.5, False)


def add_device_table(document: Document) -> None:
    """设备清单表：3 列（设备名称、数量、单价），表头 + 5 行数据。"""
    rows = [
        ("设备名称", "数量", "单价"),
        ("智能巡检终端 CR-200", "12 台", "86,500.00"),
        ("充电基座 CB-12", "12 台", "8,600.00"),
        ("边缘计算网关 EG-30", "4 台", "52,000.00"),
        ("安装支架 ST-08", "12 套", "1,200.00"),
        ("调试工具包 TK-05", "2 套", "4,200.00"),
    ]
    table = document.add_table(rows=len(rows), cols=3)
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    widths = (Cm(8.4), Cm(3.2), Cm(3.4))
    for r, values in enumerate(rows):
        row = table.rows[r]
        for c, value in enumerate(values):
            cell = row.cells[c]
            cell.width = widths[c]
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.first_line_indent = Pt(0)
            paragraph.paragraph_format.space_before = Pt(2)
            paragraph.paragraph_format.space_after = Pt(2)
            paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT if c == 0 else WD_ALIGN_PARAGRAPH.CENTER
            _apply_run_font(paragraph.add_run(value), BODY_FONT, 10, r == 0)


# --------------------------------------------------------------------------
# 一、设备采购合同：中文条序号「第X条」
# --------------------------------------------------------------------------
PURCHASE_TITLE = "设备采购合同"

PURCHASE_META = [
    ("合同编号：XL-CG-2026-0318", None),
    ("甲方（买受人）：星澜科技有限公司", None),
    ("乙方（出卖人）：云枢信息技术有限公司", None),
    ("签订日期：2026年3月18日", None),
    ("签订地点：甲方住所地", None),
]

PURCHASE_CLAUSES = [
    ("第一条 合同标的与设备清单",
     "甲方向乙方采购智能巡检终端及其配套设备，用于甲方自有园区的日常巡检作业。"
     "设备名称、数量与单价以本条所列设备清单为准，清单与本合同正文具有同等效力。"
     "清单所列型号、规格需要调整的，双方应另行签署书面变更文件，未经书面确认的调整对双方不发生效力。"),
    ("第二条 质量标准与技术要求",
     "乙方交付的设备应为全新未使用产品，符合国家现行强制性标准及行业推荐性标准，"
     "并满足本合同附件一《技术规格书》载明的各项参数。乙方应在交付时提供产品合格证、"
     "出厂检验报告、使用说明书及原厂保修凭证。设备经甲方验收不合格的，乙方应自费更换；"
     "更换后仍不合格的，甲方有权拒收并要求退还已付款项。"),
    ("第三条 合同价款",
     "本合同总价为人民币壹佰叁拾柒万贰仟元整（¥1,372,000.00），包含设备价款、包装费、"
     "运输费、保险费、装卸费、安装调试费及税费。除双方依本合同约定书面确认的变更外，"
     "任何一方不得单方调整合同总价。合同总价与设备清单合价不一致时，以设备清单合价为准。"),
    ("第四条 付款方式与账期",
     "合同生效后五个工作日内，甲方支付合同总价的百分之三十作为预付款。设备全部到货并"
     "验收合格后，甲方在收到乙方开具的合规增值税专用发票之日起三十日内支付合同总价的"
     "百分之六十。剩余百分之十作为质量保证金，于质保期届满且无质量异议后三十日内一次性"
     "付清。发票不合规或单据不全的，付款期限自补正之日起重新计算，由此产生的迟延不视为甲方违约。"),
    ("第五条 交付、包装与运输",
     "乙方应于2026年6月30日前将全部设备运至甲方指定的收货地点，并提前三个工作日书面"
     "通知发货明细与预计到达时间。设备包装应符合长途运输与多次装卸的要求，具备防潮、"
     "防震、防静电措施，包装物外侧应标明合同编号、设备名称、数量及储运标识。运输、装卸"
     "及途中保险费用由乙方承担，设备毁损、灭失的风险自甲方签收时起转移。"),
    ("第六条 验收与异议",
     "甲方应在设备到达后十个工作日内组织开箱检验，核对型号、数量、外观及随机文件，"
     "乙方应派员到场配合。检验发现短少、损坏或与清单不符的，甲方应制作书面记录并由双方"
     "代表签字，乙方应在收到记录后十五个工作日内补足、更换或修复。甲方逾期未组织检验且"
     "未提出书面异议的，视为外观验收合格，但不免除乙方对隐蔽瑕疵应承担的责任。"),
    ("第七条 质量保证与售后服务",
     "乙方保证设备自验收合格之日起二十四个月内正常运行。质保期内因设备自身质量问题发生"
     "故障的，乙方应在接到通知后四小时内响应、四十八小时内修复或更换，费用由乙方承担。"
     "同一设备在六个月内出现三次以上同类故障的，甲方有权要求更换新机或按折旧后退货。"
     "质保期届满后，乙方应按不高于市场价的水平提供五年有偿维修与备件供应。"),
    ("第八条 违约责任",
     "乙方逾期交付设备的，每逾期一日应按合同总价的万分之五向甲方支付违约金，累计不超过"
     "合同总价的百分之五；逾期超过三十日的，甲方有权解除合同并要求乙方赔偿实际损失。"
     "甲方逾期付款的，每逾期一日按应付未付金额的万分之五支付违约金。乙方交付的设备不符"
     "合约定的，应按合同总价的百分之十支付违约金，并继续履行更换义务。"),
    ("第九条 保密义务",
     "双方对因履行本合同知悉的对方商业秘密、技术资料、报价信息及甲方业务数据负有保密"
     "义务，未经对方书面同意不得向第三方披露，亦不得用于本合同以外的目的。保密义务自"
     "本合同签署之日起生效，在合同终止或解除后继续有效五年。法律法规要求披露的，披露方"
     "应事先通知对方，并将披露范围限制在法定最小限度内。"),
    ("第十条 知识产权归属",
     "乙方交付设备所附软件、固件及技术文档的知识产权归乙方或原权利人所有，乙方在此授予"
     "甲方在自有场所内永久、不可撤销、免许可费的使用权。甲方就设备运行形成的业务数据享"
     "有所有权，乙方仅可在履行本合同及提供售后服务所必需的范围内使用。因设备侵犯第三方"
     "知识产权导致甲方被主张权利的，乙方应负责处理并赔偿甲方由此产生的直接损失。"),
    ("第十一条 不可抗力",
     "因地震、洪水、台风、火灾、战争、瘟疫、政府行为或其他不能预见、不能避免且不能克服"
     "的客观情况，致使一方不能履行或不能完全履行本合同的，该方不承担违约责任，但应在事"
     "件发生后五日内书面通知对方，并提供有权机构出具的证明。不可抗力持续超过六十日致使"
     "合同目的不能实现的，任何一方均有权书面通知解除合同，双方按已履行部分结算。"),
    ("第十二条 争议解决",
     "因本合同引起的或与本合同有关的任何争议，双方应首先通过友好协商解决；协商不成的，"
     "任何一方均可向甲方住所地有管辖权的人民法院提起诉讼。争议解决期间，除争议事项外，"
     "双方应继续履行本合同项下其他义务。败诉方应承担胜诉方为维护自身权利支出的律师费、"
     "保全费、公证费及差旅费等合理费用。"),
]

# 超长条款：用于验证「超长条款二次切分」（正文远超 800 字符切分阈值，且单段连续）。
PURCHASE_LONG_HEADING = "第十三条 其他约定"
PURCHASE_LONG_BODY = (
    "本条系双方就前述条款未尽事项所作的补充与细化，与前述条款具有同等效力；本条与前述"
    "条款就同一事项规定不一致的，以本条为准。"
    "其一，关于验收的组织与程序：设备到达甲方指定收货地点后，甲方应在十个工作日内组织"
    "开箱检验，逐台核对型号、数量、外观、随机文件及铭牌信息，乙方应派员到场配合并承担其"
    "人员费用；检验发现短少、损坏、锈蚀或与设备清单不符的，双方代表应当场签署书面记录，"
    "乙方应在收到记录后十五个工作日内完成补足、更换或修复，因此产生的运输、装卸、检测费"
    "用由乙方承担；开箱检验仅针对可见瑕疵，不免除乙方对隐蔽瑕疵应负的责任，甲方在质保期"
    "内就设备内在质量提出异议的，乙方不得以已通过开箱检验为由抗辩；甲方逾期未组织检验且"
    "未提出书面异议的，视为外观验收合格，但该视为合格不影响甲方依本合同主张质量责任。"
    "其二，关于包装与标识：乙方应采用适合长途运输、多次装卸及露天短时存放的包装方式，具"
    "备防潮、防震、防静电、防锈及防挤压措施，精密部件应单独固定并加装缓冲材料；包装物外"
    "侧应以不易脱落的标识载明合同编号、设备名称、型号、数量、毛重、重心位置以及向上、防"
    "潮、易碎等储运图示；因包装不当造成设备损坏、锈蚀或灭失的，由乙方承担全部责任，并承"
    "担重新包装、往返运输及检测费用；包装物不予回收，甲方有权自行处置，但不得就包装物残"
    "值向乙方主张任何权利。"
    "其三，关于运输、交付与风险转移：乙方负责将全部设备运至甲方指定地点，承担运输、装卸、"
    "二次搬运及途中保险费用，并保证运输车辆符合设备装载要求；乙方应在发货前三个工作日以"
    "书面方式通知甲方发货明细、承运人名称、运单编号及预计到达时间，甲方应安排人员在约定"
    "时间接收并签署收货凭证；设备毁损、灭失的风险自甲方签署收货凭证之时起转移，但甲方无"
    "正当理由拒绝接收的，风险自拒绝接收之日起转移，因设备不符合约定而拒绝接收的除外；乙"
    "方不得以甲方未支付尾款为由留置已交付设备或拒绝提供随机文件。"
    "其四，关于保险：乙方应以自己为投保人，按合同总价的百分之一百一十为全部设备投保运输"
    "一切险，保险期间自设备离开乙方仓库时起至甲方签署收货凭证时止，保险单副本应随发货通"
    "知一并提交甲方备查；发生保险事故的，由乙方负责索赔并先行向甲方补足设备或退还相应价"
    "款，不得以保险理赔尚未结案为由拖延交付或拒绝更换；甲方自签署收货凭证之日起至质保期"
    "届满前，应自行投保财产综合险，该保险不免除乙方依本合同应承担的质量责任与赔偿责任。"
    "其五，关于知识产权归属与许可：乙方保证其交付的设备、嵌入式软件、固件及技术资料不侵"
    "犯任何第三方的专利权、著作权、商标权、商业秘密或其他权利，因第三方主张权利导致甲方"
    "被诉、被索赔或无法正常使用设备的，乙方应负责应对并承担甲方由此支出的赔偿金、和解金、"
    "律师费、公证费及差旅费等合理费用，且不得因此减少其在本合同项下的其他义务；设备随机"
    "安装的嵌入式软件，其著作权仍归乙方或原权利人所有，乙方在此授予甲方一项永久、不可撤"
    "销、免许可费、不可转授的使用许可，使用范围限于甲方及其关联方在自有生产经营场所内为"
    "生产经营目的使用该设备；甲方不得对该软件进行反向工程、反编译、拆解、修改或另行销售，"
    "但法律法规允许的除外；甲方就设备运行过程中形成的业务数据、运行日志及统计数据享有所"
    "有权，乙方仅可在履行本合同、提供售后服务及履行法定义务所必需的范围内访问和使用，不"
    "得复制留存、对外提供或用于训练其他模型；双方在履行本合同过程中共同研发形成的技术成"
    "果，其申请专利的权利由双方共有，一方转让共有份额时，另一方在同等条件下享有优先受"
    "让权，任何一方均不得单独将该成果许可给甲方的直接竞争对手。"
    "其六，关于通知与送达：双方以本合同首部载明的地址、电子邮箱、收件人及联系电话作为有"
    "效送达地址，一方变更上述信息的，应在变更后五个工作日内书面通知对方，未通知致使文书"
    "未能实际送达的，由该方承担相应后果；以专人送达的，签收之日为送达日，受送达方拒绝签"
    "收的，送达人注明情况并留存影像资料之日视为送达日；以快递方式送达的，签收之日或者快"
    "递退回之日视为送达日；以电子邮件方式送达的，邮件进入对方指定服务器之日视为送达日；"
    "上述送达地址同时适用于诉讼阶段的法律文书送达。"
    "其七，关于合同的变更、转让与解除：本合同的任何变更、补充均须由双方授权代表签署书面"
    "文件方为有效，口头约定、往来函件中的意向性表述及会议纪要中未经双方签章确认的内容，"
    "均不构成对本合同的变更；乙方不得将本合同项下义务全部或部分转让给第三方，确需分包的，"
    "应事先取得甲方书面同意，分包不免除乙方的责任；一方严重违约致使合同目的不能实现的，"
    "另一方有权书面通知解除合同，合同解除不影响守约方就已有损失主张赔偿的权利，亦不影响"
    "保密、知识产权、争议解决及通知送达条款的效力。"
    "其八，关于廉洁与合规：双方在缔约及履约过程中应遵守有关反商业贿赂、反不正当竞争、出"
    "口管制及数据安全的法律法规，不得向对方员工、其近亲属或关联方提供财物、回扣、旅游或"
    "其他不正当利益，不得串通报价或实施其他损害对方利益的行为；一方违反本款约定的，另一"
    "方有权立即解除合同，并要求违约方支付合同总价百分之二十的违约金，违约金不足以弥补损"
    "失的，违约方应就差额部分继续赔偿。"
    "其九，关于文件效力与解释：本合同附件、设备清单、技术规格书、双方签章确认的变更文件"
    "均为本合同的组成部分，与本合同正文具有同等效力，附件与正文不一致的，以正文为准，但"
    "双方另有明确约定的除外；本合同以中文书写，一式肆份，甲乙双方各执贰份，具有同等法律"
    "效力；本合同自双方法定代表人或授权代表签字并加盖单位公章之日起生效，至双方义务履行"
    "完毕之日终止。"
)


def build_purchase(path: str) -> None:
    document = Document()
    setup_styles(document)
    document.core_properties.title = "设备采购合同（虚构测试语料）"
    document.core_properties.subject = "合同风控审核评估样例"
    document.core_properties.author = "contract-risk-agent eval"

    add_title(document, PURCHASE_TITLE)
    for text, _ in PURCHASE_META:
        add_meta(document, text)
    add_note(document, "甲乙双方经友好协商，就甲方向乙方采购智能巡检设备事宜达成如下协议，共同遵守。")

    for index, (heading, body) in enumerate(PURCHASE_CLAUSES):
        add_heading(document, heading)
        add_body(document, body)
        if index == 0:
            add_note(document, "设备清单如下：")
            add_device_table(document)

    add_heading(document, PURCHASE_LONG_HEADING)
    add_body(document, PURCHASE_LONG_BODY)

    add_meta(document, "")
    add_meta(document, "甲方：星澜科技有限公司（盖章）", None)
    add_meta(document, "乙方：云枢信息技术有限公司（盖章）", None)

    document.save(path)


# --------------------------------------------------------------------------
# 二、技术服务合同：阿拉伯数字多级编号「1.1 / 1.2 / 1.2.1」
# --------------------------------------------------------------------------
SERVICE_TITLE = "技术服务合同"

SERVICE_META = [
    "合同编号：YS-JS-2026-0512",
    "甲方（委托方）：星澜科技有限公司",
    "乙方（受托方）：云枢信息技术有限公司",
    "签订日期：2026年5月12日",
    "服务地点：甲方住所地及双方确认的远程接入环境",
]

SERVICE_CLAUSES = [
    ("1.1 服务范围与目标",
     "乙方为甲方提供合同智能审核系统的规则配置、解析链路调优与上线支持服务。服务目标是在"
     "甲方提供的评估集上完成条款切分与类型标注，并输出可追溯的审核结论。具体服务内容、交付"
     "物与验收标准以附件一《服务说明书》为准。"),
    ("1.2 服务内容",
     "服务内容包括规则库梳理与条款类型标注规范的落地、文档解析与切分链路的参数调优、审核"
     "提示词模板的编写，以及上线前的回归测试与缺陷修复。双方项目负责人应每周召开一次进度"
     "例会，会议结论以书面纪要形式确认后执行。"),
    ("1.3 服务范围清单",
     "双方确认本次服务范围包括下列三项，超出范围的工作应另行签署补充协议并单独计费。乙方"
     "不得以范围清单未列明为由拒绝履行与实现服务目标直接相关的必要工作。"),
    ("1.3.1 规则库梳理与标注规范",
     "乙方应在服务启动后十五个工作日内完成甲方现有规则库的梳理，输出规则清单、优先级说明"
     "与条款类型标注规范，并对甲方指定的三名业务人员提供不少于四小时的培训，培训材料留存"
     "甲方备查。"),
    ("1.3.2 解析与切分链路调优",
     "乙方应针对甲方实际合同文本调整条款切分阈值、窗口重叠比例与类型判定关键词，使中文条"
     "序号、阿拉伯数字多级编号、中文括号序号三类标记的切分结果与人工标注一致率达到双方约"
     "定的验收口径。"),
    ("1.3.3 上线前回归测试与缺陷修复",
     "乙方应在上线前完成不少于两轮全链路回归测试，提交测试报告与缺陷清单，并对阻断级与严"
     "重级缺陷在五个工作日内完成修复，修复后重新执行相关用例，测试记录随验收资料一并移交。"),
    ("2.1 服务期限",
     "服务期限自2026年6月1日起至2026年11月30日止，共计六个月。经双方书面确认可以延长，"
     "延长期间的费用按本合同约定的月度单价计算，任何一方不得单方缩短服务期限。"),
    ("2.2 服务地点与方式",
     "服务以乙方远程支持为主，现场支持累计不超过二十个工作日。甲方应提前五个工作日提出"
     "现场支持需求，并提供必要的办公条件、测试环境与系统访问权限。"),
    ("3.1 服务费总额",
     "本合同服务费总额为人民币肆拾捌万元整（¥480,000.00），包含乙方人员成本、差旅费、"
     "税费及履行本合同所需的全部费用，甲方无需另行支付其他款项，双方另有书面约定的除外。"),
    ("3.2 付款方式与账期",
     "服务费按三个月一期分两期支付。乙方于每期届满后五个工作日内提交服务报告与合规发票，"
     "甲方在收到之日起三十日内支付该期费用的百分之九十，剩余百分之十于全部服务验收合格后"
     "三十日内结清。发票不合规的，付款期限自补正之日起重新计算。"),
    ("3.3 结算资料",
     "乙方提交的结算资料应包括服务报告、工时明细、交付物清单及合规发票。资料不齐的，甲方"
     "有权暂缓支付对应款项，暂缓期间不计违约金，但甲方应在收到资料后十个工作日内一次性列"
     "明需要补正的内容。"),
    ("4.1 甲方义务",
     "甲方应指定一名项目负责人对接，及时提供服务所需的资料、数据与系统权限，并在收到乙方"
     "交付物后十个工作日内完成确认或提出书面意见，逾期未反馈的视为确认，但不免除乙方对交"
     "付物质量的瑕疵担保责任。"),
    ("4.2 乙方义务",
     "乙方应委派具备相应经验的人员承担服务，保证服务人员相对稳定；更换核心人员的，应提前"
     "十个工作日书面通知甲方并取得同意。乙方不得将本合同项下主要义务分包给第三方，确需分"
     "包的应事先取得甲方书面同意。"),
    ("5.1 交付物",
     "乙方应按附件一约定的时间与形式提交交付物，包括规则清单、标注规范、调优记录、测试报告"
     "及操作手册。交付物应内容完整、可读可执行，并附必要的说明与示例。"),
    ("5.2 验收",
     "甲方应在收到交付物后十五个工作日内组织验收，验收以双方确认的验收标准为依据。验收不"
     "合格的，乙方应在十个工作日内整改并重新提交验收，整改次数不超过两次；两次整改仍不合"
     "格的，甲方有权解除合同并要求退还相应费用。"),
    ("6.1 乙方违约责任",
     "乙方未按期提交交付物或未按期完成缺陷修复的，每逾期一日按服务费总额的万分之五支付违"
     "约金，累计不超过服务费总额的百分之十；因乙方原因导致项目终止的，乙方应退还已收取但"
     "未实际提供服务的费用，并赔偿甲方由此产生的直接损失。"),
    ("6.2 甲方违约责任",
     "甲方逾期支付服务费的，每逾期一日按应付未付金额的万分之五支付违约金；甲方无正当理由"
     "单方终止服务的，应支付乙方已完成工作对应的费用，并按服务费总额的百分之十支付补偿金。"),
    ("7.1 保密义务",
     "双方对履行本合同过程中知悉的对方商业秘密、技术方案、数据样本及审核规则负有保密义"
     "务，未经书面同意不得向第三方披露或用于本合同以外的目的。保密义务在合同终止或解除后"
     "继续有效五年。"),
    ("7.2 数据安全",
     "乙方处理甲方数据应限定在甲方授权的环境内，不得将数据复制至个人设备或用于训练其他模"
     "型；服务结束后应按甲方要求删除或返还全部数据，并出具书面确认。乙方违反本款的，应按"
     "服务费总额的百分之二十支付违约金。"),
    ("8.1 知识产权归属",
     "乙方在服务前已拥有的工具、框架与方法论的知识产权归乙方所有；乙方为履行本合同专门开"
     "发的配置、规则与文档，其知识产权归甲方所有。甲方有权对上述成果进行修改与再许可，无"
     "需另行支付费用。"),
    ("8.2 第三方权利保证",
     "乙方保证其交付成果不侵犯第三方知识产权。因交付成果引发第三方主张权利的，乙方应负责"
     "应对并赔偿甲方由此产生的直接损失，包括合理的律师费与诉讼费，且不得因此中止服务。"),
    ("9.1 不可抗力",
     "因不可抗力致使一方不能履行合同的，该方应在事件发生后五日内书面通知对方并提供证明，"
     "据此免除相应责任。不可抗力持续超过三十日的，任何一方有权解除合同，双方按已履行部分"
     "结算，互不承担违约责任。"),
    ("10.1 争议解决与管辖",
     "因本合同引起的争议，双方应友好协商解决；协商不成的，提交甲方住所地有管辖权的人民法"
     "院诉讼解决。诉讼期间双方应继续履行无争议部分，败诉方承担胜诉方支出的合理律师费与差"
     "旅费。"),
    ("11.1 合同变更与解除",
     "本合同的变更与补充须经双方授权代表签署书面文件方为有效。口头承诺以及会议记录中未经"
     "双方签章确认的内容，不构成对本合同的变更。合同解除不影响保密、知识产权与争议解决条"
     "款的效力。"),
    ("11.2 通知与送达",
     "双方以合同首部载明的联系方式作为有效送达地址，变更应书面通知对方，未通知致使文书未"
     "能送达的由该方承担后果。以电子邮件送达的，邮件进入对方指定服务器之日视为送达日。"),
    ("11.3 生效与份数",
     "本合同一式肆份，甲乙双方各执贰份，自双方法定代表人或授权代表签字并加盖单位公章之日"
     "起生效，至双方义务履行完毕之日终止。附件与本合同正文不一致的，以正文为准。"),
]


def build_service(path: str) -> None:
    document = Document()
    setup_styles(document)
    document.core_properties.title = "技术服务合同（虚构测试语料）"
    document.core_properties.subject = "合同风控审核评估样例"
    document.core_properties.author = "contract-risk-agent eval"

    add_title(document, SERVICE_TITLE)
    for text in SERVICE_META:
        add_meta(document, text)
    add_note(document, "甲乙双方就合同智能审核系统的技术服务事宜，经协商一致订立本合同。")

    for heading, body in SERVICE_CLAUSES:
        add_heading(document, heading)
        add_body(document, body)

    add_meta(document, "")
    add_meta(document, "甲方：星澜科技有限公司（盖章）", None)
    add_meta(document, "乙方：云枢信息技术有限公司（盖章）", None)

    document.save(path)


# --------------------------------------------------------------------------
# 三、保密协议：中文括号序号「（一）」
# --------------------------------------------------------------------------
NDA_TITLE = "保密协议"

NDA_META = [
    "协议编号：BM-2026-0806",
    "甲方（披露方）：星澜科技有限公司",
    "乙方（接收方）：云枢信息技术有限公司",
    "签订日期：2026年8月6日",
    "签订地点：甲方住所地",
]

NDA_CLAUSES = [
    ("（一）保密信息的范围",
     "本协议所称保密信息，指一方以书面、口头、电子数据或现场演示等方式向另一方披露，并"
     "注明保密或依其性质应当保密的技术资料、经营数据、合同文本、审核规则、价格信息、客户"
     "名单及未公开的产品规划。保密信息无论以何种载体形式存在，均适用本协议。"),
    ("（二）接收方的保密义务",
     "接收方应以不低于保护自身同类信息的谨慎程度保护保密信息，且该谨慎程度不得低于合理"
     "注意义务。接收方仅可为本协议目的使用保密信息，不得复制、摘录、改编或向第三方披露。"
     "接收方应确保其接触保密信息的人员知悉并遵守本协议。"),
    ("（三）使用限制",
     "接收方不得将保密信息用于与双方合作无关的任何目的，不得进行反向工程、反编译或试图"
     "从保密信息中推导披露方的技术方案。未经披露方事先书面同意，接收方不得就保密信息申请"
     "专利、著作权登记或发表相关论文与公开材料。"),
    ("（四）人员管理与分包限制",
     "接收方应将保密信息的知悉范围限制在因履行职责确有必要知悉的员工与顾问范围内，并与他"
     "们签署不低于本协议保护水平的保密承诺。接收方不得将保密信息提供给分包方，确需提供的，"
     "应事先取得披露方书面同意并承担分包方的违约后果。"),
    ("（五）保密期限",
     "本协议自双方签署之日起生效，保密义务自保密信息披露之日起算，持续有效五年。构成商业"
     "秘密的信息，其保密义务不因本协议期限届满而终止，接收方应持续保密至该信息进入公有领"
     "域为止。"),
    ("（六）费用与结算",
     "本协议项下保密义务不单独收取保密费用。一方应另一方书面请求派员到场配合的，差旅与住"
     "宿费用由请求方承担，提供方应在费用发生后十五个工作日内提交明细及合规发票，请求方在"
     "收到之日起三十日内支付。"),
    ("（七）违约责任",
     "接收方违反本协议的，应立即停止违约行为并采取补救措施，同时按人民币贰拾万元向披露方"
     "支付违约金；违约金不足以弥补披露方实际损失的，接收方应就差额部分继续赔偿。披露方有"
     "权要求接收方返还或销毁全部保密信息载体，并出具书面确认。"),
    ("（八）免责情形",
     "保密信息属于下列情形之一的，接收方不承担保密义务：接收方在披露前已合法知悉且无保密"
     "义务的；非因接收方原因已进入公有领域的；接收方从有权披露的第三方合法取得的；依法定"
     "程序或监管机关要求披露的。因地震、洪水、战争、瘟疫等不可抗力致使保密信息意外泄露，"
     "接收方已尽合理保护义务的，不承担违约责任，但应在事件发生后三日内书面通知披露方。"),
    ("（九）争议解决与管辖",
     "因本协议引起的争议，双方应友好协商解决；协商不成的，任何一方均可向甲方住所地有管辖"
     "权的人民法院提起诉讼。本协议的订立、效力、解释与履行均适用中华人民共和国法律。"),
    ("（十）其他约定",
     "本协议构成双方就保密事项的完整约定，取代此前所有口头或书面的相关承诺。本协议的修改"
     "与补充须经双方授权代表签署书面文件方为有效。本协议部分条款被认定无效的，不影响其余"
     "条款的效力。"),
    ("（十一）生效与份数",
     "本协议一式贰份，甲乙双方各执壹份，自双方法定代表人或授权代表签字并加盖单位公章之日"
     "起生效。本协议终止后，双方在合作过程中形成的保密义务继续有效，不因合作终止而免除。"),
]


def build_nda(path: str) -> None:
    document = Document()
    setup_styles(document)
    document.core_properties.title = "保密协议（虚构测试语料）"
    document.core_properties.subject = "合同风控审核评估样例"
    document.core_properties.author = "contract-risk-agent eval"

    add_title(document, NDA_TITLE)
    for text in NDA_META:
        add_meta(document, text)
    add_note(document, "鉴于双方就合同智能审核系统的评估与对接开展合作，可能相互披露保密信息，"
                       "为明确保密义务，双方经协商一致订立本协议。")

    for heading, body in NDA_CLAUSES:
        add_heading(document, heading)
        add_body(document, body)

    add_meta(document, "")
    add_meta(document, "甲方：星澜科技有限公司（盖章）", None)
    add_meta(document, "乙方：云枢信息技术有限公司（盖章）", None)

    document.save(path)


# --------------------------------------------------------------------------
# 自检
# --------------------------------------------------------------------------
def _has_auto_numbering(document: Document) -> list[str]:
    """返回带自动编号（numPr）的段落文本，用于证伪「编号写在行首」这一约束。"""
    offenders = []
    for paragraph in document.paragraphs:
        p_pr = paragraph._p.pPr
        if p_pr is not None and p_pr.numPr is not None:
            offenders.append(paragraph.text[:30])
    return offenders


def _sections_have_content(document: Document) -> bool:
    for section in document.sections:
        if section.header.paragraphs and any(p.text.strip() for p in section.header.paragraphs):
            return True
        if section.footer.paragraphs and any(p.text.strip() for p in section.footer.paragraphs):
            return True
    return False


def _starts_with_marker(text: str) -> bool:
    """按解析器的三类标记判断该行是否为条款起始行。"""
    return bool(CN_MARKER.match(text) or ARABIC_MARKER.match(text) or PAREN_MARKER.match(text))


def _body_lengths(document: Document) -> list[tuple[str, int, int]]:
    """返回 (条款标题, 正文汉字数, 正文总字符数)；只统计标题行紧随其后的正文段。"""
    lengths = []
    paragraphs = document.paragraphs
    for index, paragraph in enumerate(paragraphs):
        text = paragraph.text.strip()
        if not text or not _starts_with_marker(text):
            continue
        if index + 1 < len(paragraphs):
            nxt = paragraphs[index + 1].text.strip()
            if nxt and not _starts_with_marker(nxt):
                lengths.append((text, len(HANZI.findall(nxt)), len(nxt)))
    return lengths


def verify() -> bool:
    report: list[tuple[str, bool, str]] = []
    files = {
        "purchase": os.path.join(SAMPLES_DIR, "purchase_contract.docx"),
        "service": os.path.join(SAMPLES_DIR, "service_contract.docx"),
        "nda": os.path.join(SAMPLES_DIR, "nda_contract.docx"),
    }
    docs: dict[str, Document] = {}

    print("=" * 78)
    print("一、可打开性与结构统计")
    print("=" * 78)
    for key, path in files.items():
        try:
            docs[key] = Document(path)
            size = os.path.getsize(path)
            report.append((f"打开 {os.path.basename(path)}", True, f"{size} 字节"))
            print(f"[PASS] 打开 {os.path.basename(path)}：{size} 字节")
        except Exception as exc:  # noqa: BLE001 - 自检脚本需要报告任意异常
            report.append((f"打开 {os.path.basename(path)}", False, repr(exc)))
            print(f"[FAIL] 打开 {os.path.basename(path)}：{exc!r}")
    if len(docs) != len(files):
        return summarize(report)

    print()
    print(f"{'文件':<26}{'段落数':>8}{'表格数':>8}{'字节数':>12}")
    for key, path in files.items():
        document = docs[key]
        print(f"{os.path.basename(path):<26}{len(document.paragraphs):>8}{len(document.tables):>8}"
              f"{os.path.getsize(path):>12}")

    print()
    print("=" * 78)
    print("二、超长条款（purchase_contract.docx）")
    print("=" * 78)
    long_doc = docs["purchase"]
    long_body = None
    for index, paragraph in enumerate(long_doc.paragraphs):
        if paragraph.text.strip().startswith(PURCHASE_LONG_HEADING.split()[0]):
            if index + 1 < len(long_doc.paragraphs):
                long_body = long_doc.paragraphs[index + 1].text
            break
    hanzi = len(HANZI.findall(long_body or ""))
    total = len(long_body or "")
    ok = long_body is not None and hanzi >= 1200 and total > 800
    report.append(("超长条款汉字数≥1200", ok, f"汉字 {hanzi}，总字符 {total}"))
    print(f"超长条款标题：{PURCHASE_LONG_HEADING}")
    print(f"正文汉字数：{hanzi}    正文字符数（含标点）：{total}    "
          f"切分阈值 800 字符：{'超过' if total > 800 else '未超过'}")
    print(f"[{'PASS' if ok else 'FAIL'}] 汉字数≥1200 且超过单条切分阈值")

    print()
    print("=" * 78)
    print("三、三类条款标记命中统计")
    print("=" * 78)
    expected = {
        "purchase": (CHECK_CN, "中文条序号 ^第[一二三四五六七八九十]+条"),
        "service": (CHECK_ARABIC, r"多级编号 ^\d{1,2}\.\d{1,2}"),
        "nda": (CHECK_PAREN, "中文括号序号 ^[（(][一二三四五六七八九十]+[）)]"),
    }
    for key, path in files.items():
        document = docs[key]
        pattern, label = expected[key]
        lines = [p.text.strip() for p in document.paragraphs if p.text.strip()]
        hits = [line for line in lines if pattern.match(line)]
        first = hits[0] if hits else ""
        first_ok = bool(first) and pattern.match(first) is not None
        minimum = 10 if key in ("purchase", "service") else 8
        ok = first_ok and len(hits) >= minimum
        report.append((f"{os.path.basename(path)} 标记命中≥{minimum}", ok,
                       f"命中 {len(hits)} 行，首行：{first[:24]}"))
        print(f"{os.path.basename(path):<26} 命中 {len(hits):>3} 行   首行：{first[:30]}")
        print(f"    正则：{label}    要求≥{minimum} 行  ->  {'PASS' if ok else 'FAIL'}")

    print()
    print("=" * 78)
    print("四、其他约束")
    print("=" * 78)
    # 4.1 表格结构
    table = long_doc.tables[0] if long_doc.tables else None
    header = [c.text.strip() for c in table.rows[0].cells] if table is not None else []
    table_ok = (table is not None and len(table.columns) == 3 and len(table.rows) >= 4
                and header == ["设备名称", "数量", "单价"])
    report.append(("设备清单表 3 列且≥4 行", table_ok,
                   f"{len(table.rows) if table else 0} 行 × {len(table.columns) if table else 0} 列，表头 {header}"))
    print(f"[{'PASS' if table_ok else 'FAIL'}] 设备清单表：{len(table.rows) if table else 0} 行 × "
          f"{len(table.columns) if table else 0} 列，表头 {header}")
    if table is not None:
        for row in table.rows[1:]:
            print("      " + " | ".join(cell.text.strip() for cell in row.cells))

    # 4.2 无自动编号
    numbered = {key: _has_auto_numbering(document) for key, document in docs.items()}
    auto_ok = all(not v for v in numbered.values())
    report.append(("无 Word 自动编号（numPr）", auto_ok, str(numbered)))
    print(f"[{'PASS' if auto_ok else 'FAIL'}] 无自动编号：{numbered}")

    # 4.3 无页眉页脚
    hf_ok = not any(_sections_have_content(document) for document in docs.values())
    report.append(("无页眉页脚内容", hf_ok, ""))
    print(f"[{'PASS' if hf_ok else 'FAIL'}] 页眉页脚无内容")

    # 4.4 无批注部件、超链接、宏
    import zipfile

    forbidden = []
    for key, path in files.items():
        with zipfile.ZipFile(path) as package:
            names = package.namelist()
            for name in names:
                if name.startswith("word/comments") or "vbaProject" in name or name.endswith(".bin"):
                    forbidden.append(f"{os.path.basename(path)}:{name}")
            document_xml = package.read("word/document.xml").decode("utf-8")
            if "<w:hyperlink" in document_xml:
                forbidden.append(f"{os.path.basename(path)}:hyperlink")
    clean_ok = not forbidden
    report.append(("无批注/超链接/宏", clean_ok, str(forbidden)))
    print(f"[{'PASS' if clean_ok else 'FAIL'}] 无批注、超链接与宏：{forbidden or '无'}")

    # 4.5 正文长度区间（超长条款除外）
    for key in ("purchase", "service", "nda"):
        entries = _body_lengths(docs[key])
        if key == "purchase":
            entries = [e for e in entries if not e[0].startswith(PURCHASE_LONG_HEADING.split()[0])]
        short = [e for e in entries if e[2] < 40]
        long_ones = [e for e in entries if e[2] > 250]
        ok = not short and not long_ones
        detail = (f"共 {len(entries)} 段，最短 {min((e[2] for e in entries), default=0)} 字，"
                  f"最长 {max((e[2] for e in entries), default=0)} 字")
        report.append((f"{os.path.basename(files[key])} 正文 40–250 字", ok,
                       detail + (f"；越界 {[e[0][:12] for e in short + long_ones]}" if not ok else "")))
        print(f"[{'PASS' if ok else 'FAIL'}] {os.path.basename(files[key])} 条款正文长度区间：{detail}")
        if not ok:
            for h, _, n in short + long_ones:
                print(f"      越界：{h} -> {n} 字")

    # 4.6 首部要素
    for key in ("purchase", "service", "nda"):
        text = "\n".join(p.text for p in docs[key].paragraphs)
        need = ["星澜科技有限公司", "云枢信息技术有限公司", "2026年"]
        missing = [n for n in need if n not in text]
        report.append((f"{os.path.basename(files[key])} 首部要素", not missing, str(missing)))
        print(f"[{'PASS' if not missing else 'FAIL'}] {os.path.basename(files[key])} 含虚构主体与 2026 年日期："
              f"{missing or '齐备'}")

    # 4.7 语料洁净度：无占位符、无真实主体
    placeholder = ("XX", "xx", "待填", "待定", "占位", "【", "】", "＿＿", "TODO")
    allow_orgs = {"星澜科技有限公司", "云枢信息技术有限公司"}
    dirty = {}
    for key, path in files.items():
        document = docs[key]
        text = "\n".join(p.text for p in document.paragraphs)
        text += "\n".join(cell.text for table_ in document.tables
                          for row in table_.rows for cell in row.cells)
        hits = [token for token in placeholder if token in text]
        orgs = {m for m in re.findall(r"[\u4e00-\u9fff]{2,12}(?:有限公司|股份有限公司)", text)}
        extra = sorted(orgs - allow_orgs)
        if hits or extra:
            dirty[os.path.basename(path)] = {"占位符": hits, "非虚构主体": extra}
    clean = not dirty
    report.append(("语料洁净度（无占位符/真实主体）", clean, str(dirty)))
    print(f"[{'PASS' if clean else 'FAIL'}] 语料洁净度：{dirty or '仅含两个虚构主体，无占位符'}")

    # 4.8 表格单元格无空值
    empty_cells = [] if table is None else [
        f"r{r}c{c}" for r, row in enumerate(table.rows) for c, cell in enumerate(row.cells)
        if not cell.text.strip()]
    cells_ok = table is not None and not empty_cells
    report.append(("表格单元格无空值", cells_ok, str(empty_cells)))
    print(f"[{'PASS' if cells_ok else 'FAIL'}] 表格单元格无空值：{empty_cells or '无'}")

    # 4.9 表格在正文流中的位置：紧跟第一条、位于第二条之前（表格二次还原用）
    body_children = list(long_doc.element.body.iterchildren())
    tbl_index = next((i for i, node in enumerate(body_children)
                      if node.tag == qn("w:tbl")), -1)
    first_index = next((i for i, node in enumerate(body_children)
                        if node.tag == qn("w:p") and "第一条" in "".join(node.itertext())), -1)
    second_index = next((i for i, node in enumerate(body_children)
                         if node.tag == qn("w:p") and "第二条" in "".join(node.itertext())), -1)
    order_ok = -1 < first_index < tbl_index < second_index
    report.append(("表格位于第一条与第二条之间", order_ok,
                   f"第一条@{first_index} 表格@{tbl_index} 第二条@{second_index}"))
    print(f"[{'PASS' if order_ok else 'FAIL'}] 表格在正文流中位于第一条之后、第二条之前")

    return summarize(report)


def summarize(report: list[tuple[str, bool, str]]) -> bool:
    failed = [item for item in report if not item[1]]
    print()
    print("=" * 78)
    print("校验汇总")
    print("=" * 78)
    for name, ok, detail in report:
        print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f"：{detail}" if detail else ""))
    print()
    if failed:
        print(f"结果：{len(failed)} 项未通过，共 {len(report)} 项")
        return False
    print(f"结果：全部 {len(report)} 项通过")
    return True


def main() -> int:
    os.makedirs(SAMPLES_DIR, exist_ok=True)
    build_purchase(os.path.join(SAMPLES_DIR, "purchase_contract.docx"))
    build_service(os.path.join(SAMPLES_DIR, "service_contract.docx"))
    build_nda(os.path.join(SAMPLES_DIR, "nda_contract.docx"))
    print(f"样例已生成：{SAMPLES_DIR}\n")
    return 0 if verify() else 1


if __name__ == "__main__":
    sys.exit(main())
