import json
from collections import defaultdict
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
METADATA_PATH = ROOT / "work" / "mysql_metadata.json"
OUTPUT_PATH = ROOT / "outputs" / "元数据采集_submission数据库.docx"

APP_NAMES = {
    "auditors": "用户与权限管理",
    "lng_report_tasks": "LNG报告任务管理",
    "lng_reports": "LNG报告管理",
    "market_hotspots": "市场热点管理",
    "price_index": "价格指数管理",
    "submissions": "数据报送与审核",
    "users": "用户管理",
}

TABLE_DESCRIPTIONS = {
    "auditors": "维护审核人员账号及启用状态。",
    "lng_report_tasks": "记录LNG报告生成任务的类型、状态、进度、结果和异常信息。",
    "lng_reports": "保存按日期生成的LNG报告提纲、章节、来源和执行信息。",
    "market_hotspots": "保存按日期生成的市场热点候选项、摘要、来源和执行信息。",
    "price_index": "保存日期对应的加权价格指数及记录创建时间。",
    "submissions": "保存用户按日期报送的数据，以及价格、气量和审核结果。",
    "users": "维护用户、企业名称、管理员联系电话及更新时间。",
}

FIELD_NAMES = {
    "user_id": "用户标识",
    "enabled": "启用状态",
    "updated_at": "更新时间",
    "id": "记录标识",
    "task_type": "任务类型",
    "status": "处理状态",
    "progress": "处理进度",
    "message": "处理消息",
    "result": "任务结果",
    "error_message": "错误信息",
    "created_at": "创建时间",
    "report_date": "报告日期",
    "outline": "报告提纲",
    "sections": "报告章节",
    "source": "数据来源",
    "execution_time": "执行耗时",
    "candidates": "热点候选项",
    "summary": "热点摘要",
    "date": "数据日期",
    "weighted_price": "加权价格",
    "value": "报送值",
    "submitted_at": "报送时间",
    "price": "报送价格",
    "volume": "报送气量",
    "approved_price": "审核价格",
    "approved_volume": "审核气量",
    "approved_by": "审核人",
    "approved_at": "审核时间",
    "company_name": "企业名称",
    "admin_phone": "管理员联系电话",
}

FIELD_PURPOSES = {
    "user_id": "标识用户，并用于关联用户、审核人员或报送记录。",
    "enabled": "表示审核人员账号是否启用。",
    "updated_at": "记录该行最后一次更新时间。",
    "id": "唯一标识当前业务记录。",
    "task_type": "区分报告任务的业务类型。",
    "status": "记录任务、报告或热点生成流程的当前状态。",
    "progress": "记录任务完成进度。",
    "message": "保存任务处理过程中的提示信息。",
    "result": "以JSON结构保存任务执行结果。",
    "error_message": "保存任务或生成流程失败时的错误信息。",
    "created_at": "记录该行创建时间。",
    "report_date": "标识报告或热点内容对应的业务日期。",
    "outline": "保存LNG报告的文本提纲。",
    "sections": "以JSON结构保存LNG报告章节内容。",
    "source": "记录内容生成或数据采集的来源。",
    "execution_time": "记录生成流程的执行耗时。",
    "candidates": "以JSON结构保存市场热点候选项。",
    "summary": "保存市场热点摘要文本。",
    "date": "记录价格指数或报送数据对应的日期。",
    "weighted_price": "保存计算后的加权价格指数。",
    "value": "保存用户提交的通用数值。",
    "submitted_at": "记录用户完成报送的时间。",
    "price": "保存用户报送的价格。",
    "volume": "保存用户报送的气量。",
    "approved_price": "保存审核确认后的价格。",
    "approved_volume": "保存审核确认后的气量。",
    "approved_by": "记录执行审核的用户标识。",
    "approved_at": "记录审核完成时间。",
    "company_name": "保存用户所属企业名称。",
    "admin_phone": "保存企业管理员联系电话。",
}


def set_cell_shading(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=90, start=100, bottom=90, end=100):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_repeat_table_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_table_borders(table, color="D9D9D9", size="6"):
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.find(qn("w:tblBorders"))
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = borders.find(qn(f"w:{edge}"))
        if element is None:
            element = OxmlElement(f"w:{edge}")
            borders.append(element)
        element.set(qn("w:val"), "single")
        element.set(qn("w:sz"), size)
        element.set(qn("w:color"), color)


def set_font(run, name="Arial Unicode MS", east_asia="Arial Unicode MS", size=9, bold=False, color="000000"):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = RGBColor.from_string(color)
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), east_asia)


def style_paragraph(paragraph, size=9, bold=False, color="000000", alignment=None):
    if alignment is not None:
        paragraph.alignment = alignment
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(0)
    paragraph.paragraph_format.line_spacing = 1.12
    for run in paragraph.runs:
        set_font(run, size=size, bold=bold, color=color)


def add_text(cell, text, bold=False, color="000000", size=8.5, alignment=WD_ALIGN_PARAGRAPH.LEFT):
    cell.text = ""
    p = cell.paragraphs[0]
    p.alignment = alignment
    run = p.add_run("" if text is None else str(text))
    set_font(run, size=size, bold=bold, color=color)
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.08
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    set_cell_margins(cell)


def add_table(doc, headers, rows, widths, font_size=8.5):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_table_borders(table)
    header = table.rows[0]
    set_repeat_table_header(header)
    for idx, label in enumerate(headers):
        header.cells[idx].width = Inches(widths[idx])
        set_cell_shading(header.cells[idx], "1F4E78")
        add_text(header.cells[idx], label, bold=True, color="FFFFFF", size=font_size, alignment=WD_ALIGN_PARAGRAPH.CENTER)
    for row_index, values in enumerate(rows):
        row = table.add_row()
        if row_index % 2 == 1:
            for cell in row.cells:
                set_cell_shading(cell, "F3F6FA")
        for idx, value in enumerate(values):
            row.cells[idx].width = Inches(widths[idx])
            alignment = WD_ALIGN_PARAGRAPH.CENTER if idx in (0, 1) else WD_ALIGN_PARAGRAPH.LEFT
            add_text(row.cells[idx], value, size=font_size, alignment=alignment)
    return table


def format_column_metadata(column):
    parts = [f"类型 {column['COLUMN_TYPE']}"]
    parts.append("必填" if column["IS_NULLABLE"] == "NO" else "可空")
    if column["COLUMN_KEY"] == "PRI":
        parts.append("主键")
    elif column["COLUMN_KEY"] == "UNI":
        parts.append("唯一索引")
    elif column["COLUMN_KEY"] == "MUL":
        parts.append("普通索引")
    if column["COLUMN_DEFAULT"] is not None:
        parts.append(f"默认值 {column['COLUMN_DEFAULT']}")
    if column["EXTRA"]:
        parts.append(column["EXTRA"])
    return "；".join(parts)


def build_document():
    metadata = json.loads(METADATA_PATH.read_text(encoding="utf-8"))
    tables = metadata["tables"]
    columns = metadata["columns"]
    columns_by_table = defaultdict(list)
    for column in columns:
        columns_by_table[column["TABLE_NAME"]].append(column)

    doc = Document()
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(0.65)
    section.bottom_margin = Inches(0.65)
    section.left_margin = Inches(0.58)
    section.right_margin = Inches(0.58)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Arial Unicode MS"
    normal.font.size = Pt(10.5)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial Unicode MS")
    for style_name, size in (("Title", 20), ("Heading 1", 14), ("Heading 2", 11.5)):
        style = styles[style_name]
        style.font.name = "Arial Unicode MS"
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor(0, 0, 0)
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial Unicode MS")
        style.paragraph_format.space_before = Pt(10 if style_name != "Title" else 0)
        style.paragraph_format.space_after = Pt(6)

    title = doc.add_paragraph(style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.add_run("submission 数据库元数据采集说明")
    style_paragraph(title, size=20, bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.add_run("依据元数据采集模板整理")
    style_paragraph(subtitle, size=10.5, color="5A5A5A", alignment=WD_ALIGN_PARAGRAPH.CENTER)
    subtitle.paragraph_format.space_after = Pt(12)

    intro = doc.add_paragraph()
    intro.add_run(
        "本说明记录 submission 数据库的表和字段结构。采集结果共包含 "
        f"{len(tables)} 张表、{len(columns)} 个字段。数据库原生表注释和字段注释均为空，"
        "文档中的中文名称与业务说明依据表名、字段名、数据类型、索引和默认值整理，供元数据登记与复核使用。"
    )
    style_paragraph(intro, size=10.5)
    intro.paragraph_format.space_after = Pt(9)

    summary_rows = [
        ("数据库名称", metadata["server"]["database_name"]),
        ("数据库版本", f"MySQL {metadata['server']['version']}") ,
        ("表数量", str(len(tables))),
        ("字段数量", str(len(columns))),
        ("采集范围", "仅表结构、字段结构、索引标识和结构统计，不包含业务数据样本"),
    ]
    add_table(doc, ["项目", "采集结果"], summary_rows, [1.55, 5.55], font_size=9)

    h1 = doc.add_paragraph("表信息", style="Heading 1")
    style_paragraph(h1, size=14, bold=True)
    table_rows = []
    for table in tables:
        name = table["TABLE_NAME"]
        field_names = "、".join(col["COLUMN_NAME"] for col in columns_by_table[name][:5])
        if len(columns_by_table[name]) > 5:
            field_names += " 等"
        table_rows.append((
            "submission",
            name,
            APP_NAMES.get(name, "待确认"),
            TABLE_DESCRIPTIONS.get(name, "根据表结构整理，业务含义待确认。"),
            f"字段示例：{field_names}",
        ))
    add_table(
        doc,
        ["所属数据库\n所属业务系统", "表名", "所属应用\n所属系统菜单名称", "表描述", "示例"],
        table_rows,
        [1.05, 1.25, 1.35, 2.35, 1.1],
        font_size=7.8,
    )

    h1 = doc.add_paragraph("表字段信息", style="Heading 1")
    style_paragraph(h1, size=14, bold=True)
    explanation = doc.add_paragraph()
    explanation.add_run("指标描述包含字段用途和数据库结构约束。表名与字段名称保留数据库中的原始英文名称。")
    style_paragraph(explanation, size=9.5, color="404040")
    explanation.paragraph_format.space_after = Pt(6)

    for table in tables:
        table_name = table["TABLE_NAME"]
        h2 = doc.add_paragraph(f"{table_name} {APP_NAMES.get(table_name, '')}", style="Heading 2")
        style_paragraph(h2, size=11.5, bold=True)
        field_rows = []
        for column in columns_by_table[table_name]:
            field = column["COLUMN_NAME"]
            purpose = FIELD_PURPOSES.get(field, "记录该字段对应的业务信息。")
            structural = format_column_metadata(column)
            field_rows.append((table_name, field, FIELD_NAMES.get(field, field), f"{purpose} {structural}。"))
        add_table(
            doc,
            ["表名", "字段名称", "指标名称", "指标描述"],
            field_rows,
            [1.35, 1.55, 1.25, 3.55],
            font_size=8.1,
        )

    h1 = doc.add_paragraph("说明口径", style="Heading 1")
    style_paragraph(h1, size=14, bold=True)
    notes = [
        "表行数来自 MySQL information_schema 的结构统计，InnoDB 统计可能为估算值，因此未作为业务数据量结论写入表信息。",
        "文档没有采集或展示用户、企业、联系电话、报送值、价格、气量、报告正文等业务数据。",
        "中文应用名称、指标名称和指标描述为结构推断。正式入库前应由系统负责人确认菜单名称、指标口径、单位和敏感级别。",
    ]
    for text in notes:
        p = doc.add_paragraph(style="List Bullet")
        p.add_run(text)
        style_paragraph(p, size=10)

    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer.add_run("submission 数据库元数据采集说明")
    style_paragraph(footer, size=8, color="777777", alignment=WD_ALIGN_PARAGRAPH.CENTER)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUTPUT_PATH)
    print(OUTPUT_PATH)


if __name__ == "__main__":
    build_document()
