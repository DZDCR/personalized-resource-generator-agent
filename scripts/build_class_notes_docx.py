# -*- coding: utf-8 -*-
"""Build the Word deliverable for software-engineering class notes 09."""

from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_ALIGN_VERTICAL, WD_ROW_HEIGHT_RULE, WD_TABLE_ALIGNMENT
from docx.enum.text import (
    WD_ALIGN_PARAGRAPH,
    WD_BREAK,
    WD_LINE_SPACING,
    WD_TAB_ALIGNMENT,
)
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "课堂笔记9"
ASSETS = OUT_DIR / "assets"
OUT_DOCX = OUT_DIR / "软件工程课堂笔记9.docx"

TEAL = "174A5B"
TEAL_2 = "2C6E7A"
TEAL_3 = "4C8C8C"
GOLD = "B8862E"
AMBER = "A66A1F"
INK = "22313A"
GRAY = "5F6F76"
LIGHT = "F2F7F5"
LIGHT_2 = "F7FAF9"
CREAM = "FBF5E8"
BORDER = "D6E1E4"
WHITE = "FFFFFF"
RED = "A33A2B"

BODY_FONT_CN = "宋体"
HEAD_FONT_CN = "微软雅黑"
CODE_FONT = "Consolas"
CONTENT_W = 16.0

TABLE_NO = 0
FIGURE_NO = 0


# ---------------------------------------------------------------------------
# low-level xml helpers
# ---------------------------------------------------------------------------

def rgb(value: str) -> RGBColor:
    return RGBColor.from_string(value)


def set_run_font(run, size=10.5, bold=False, color=INK,
                 ascii_font=BODY_FONT_CN, east_font=BODY_FONT_CN, italic=False):
    run.font.name = ascii_font
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = rgb(color)
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.rFonts
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), ascii_font)
    rfonts.set(qn("w:hAnsi"), ascii_font)
    rfonts.set(qn("w:eastAsia"), east_font)
    rfonts.set(qn("w:cs"), ascii_font)


def set_char_spacing(run, twips=30):
    rpr = run._element.get_or_add_rPr()
    spacing = OxmlElement("w:spacing")
    spacing.set(qn("w:val"), str(twips))
    rpr.append(spacing)


def shade_cell(cell, fill: str):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def set_cell_border(cell, **edges):
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        if edge not in edges:
            continue
        spec = edges[edge]
        tag = f"w:{edge}"
        element = borders.find(qn(tag))
        if element is None:
            element = OxmlElement(tag)
            borders.append(element)
        for key in ("val", "sz", "space", "color"):
            if key in spec:
                element.set(qn(f"w:{key}"), str(spec[key]))


def set_cell_margins(cell, top=60, start=90, bottom=60, end=90):
    tc_pr = cell._tc.get_or_add_tcPr()
    mar = tc_pr.first_child_found_in("w:tcMar")
    if mar is None:
        mar = OxmlElement("w:tcMar")
        tc_pr.append(mar)
    for tag, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = OxmlElement(f"w:{tag}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        mar.append(node)


def set_table_borders(table, color=BORDER, size=4, inside_v=True):
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = OxmlElement(f"w:{edge}")
        if edge == "insideV" and not inside_v:
            element.set(qn("w:val"), "nil")
        else:
            element.set(qn("w:val"), "single")
            element.set(qn("w:sz"), str(size))
            element.set(qn("w:space"), "0")
            element.set(qn("w:color"), color)
        borders.append(element)
    tbl_pr.append(borders)


def set_table_no_borders(table):
    # The default table style can still paint cell borders in LibreOffice/Word.
    # Set an explicit borderless style and clear each cell's direct borders.
    try:
        table.style = doc.styles["Normal Table"]
    except KeyError:
        pass
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = OxmlElement(f"w:{edge}")
        element.set(qn("w:val"), "nil")
        borders.append(element)
    tbl_pr.append(borders)
    for row in table.rows:
        for cell in row.cells:
            set_cell_border(
                cell,
                top={"val": "nil"},
                left={"val": "nil"},
                bottom={"val": "nil"},
                right={"val": "nil"},
            )


def set_table_layout(table, widths):
    tbl_pr = table._tbl.tblPr
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tbl_pr.append(layout)
    tbl_w = OxmlElement("w:tblW")
    tbl_w.set(qn("w:type"), "dxa")
    tbl_w.set(qn("w:w"), str(int(sum(widths) * 567)))
    tbl_pr.append(tbl_w)


def set_row_props(row, header=False, cant_split=True, height=None):
    tr_pr = row._tr.get_or_add_trPr()
    if cant_split:
        tr_pr.append(OxmlElement("w:cantSplit"))
    if header:
        tr_pr.append(OxmlElement("w:tblHeader"))
    if height is not None:
        row.height = Cm(height)
        row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST


def set_paragraph_border(paragraph, edge="bottom", color=TEAL, size=8, space=4):
    p_pr = paragraph._p.get_or_add_pPr()
    pbdr = p_pr.find(qn("w:pBdr"))
    if pbdr is None:
        pbdr = OxmlElement("w:pBdr")
        p_pr.append(pbdr)
    element = OxmlElement(f"w:{edge}")
    element.set(qn("w:val"), "single")
    element.set(qn("w:sz"), str(size))
    element.set(qn("w:space"), str(space))
    element.set(qn("w:color"), color)
    pbdr.append(element)


def add_field(paragraph, field_code, placeholder="1"):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = f" {field_code} "
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = placeholder
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instr)
    run._r.append(separate)
    run._r.append(text)
    run._r.append(end)
    return run


# ---------------------------------------------------------------------------
# document scaffolding
# ---------------------------------------------------------------------------

doc = Document()
doc.core_properties.title = "软件工程课堂笔记 9：性能优化与在线实时聊天应用设计"
doc.core_properties.subject = "软件工程课程笔记与课堂任务设计"
doc.core_properties.author = "软件工程课程项目组"
doc.core_properties.keywords = "秒杀；前端性能；WebSocket；消息同步；读扩散；写扩散"
doc.core_properties.comments = "根据课堂笔记 9 整理，并完成课堂任务设计。"

section = doc.sections[0]
section.page_width = Cm(21.0)
section.page_height = Cm(29.7)
section.top_margin = Cm(2.3)
section.bottom_margin = Cm(2.2)
section.left_margin = Cm(2.5)
section.right_margin = Cm(2.3)
section.header_distance = Cm(1.05)
section.footer_distance = Cm(1.0)
section.different_first_page_header_footer = True

# Normal style
normal = doc.styles["Normal"]
normal.font.name = BODY_FONT_CN
normal.font.size = Pt(10.5)
normal.font.color.rgb = rgb(INK)
normal.element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT_CN)
normal.paragraph_format.line_spacing = 1.4
normal.paragraph_format.space_before = Pt(0)
normal.paragraph_format.space_after = Pt(4)
normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

# Heading styles
for name, size, color, before, after in (
    ("Heading 1", 17, TEAL, 18, 8),
    ("Heading 2", 12.5, TEAL_2, 12, 5),
):
    st = doc.styles[name]
    st.font.name = HEAD_FONT_CN
    st.font.size = Pt(size)
    st.font.bold = True
    st.font.color.rgb = rgb(color)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT_CN)
    st.paragraph_format.space_before = Pt(before)
    st.paragraph_format.space_after = Pt(after)
    st.paragraph_format.line_spacing = 1.15
    st.paragraph_format.keep_with_next = True

# caption styles
table_cap = doc.styles.add_style("Table Caption", WD_STYLE_TYPE.PARAGRAPH)
table_cap.base_style = doc.styles["Normal"]
table_cap.font.name = HEAD_FONT_CN
table_cap.font.size = Pt(8.8)
table_cap.font.color.rgb = rgb(GRAY)
table_cap.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT_CN)
table_cap.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
table_cap.paragraph_format.space_before = Pt(6)
table_cap.paragraph_format.space_after = Pt(3)
table_cap.paragraph_format.keep_with_next = True

fig_cap = doc.styles.add_style("Figure Caption", WD_STYLE_TYPE.PARAGRAPH)
fig_cap.base_style = doc.styles["Normal"]
fig_cap.font.name = HEAD_FONT_CN
fig_cap.font.size = Pt(8.8)
fig_cap.font.color.rgb = rgb(GRAY)
fig_cap.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT_CN)
fig_cap.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
fig_cap.paragraph_format.space_before = Pt(2)
fig_cap.paragraph_format.space_after = Pt(8)

# header / footer
header = section.header
header.is_linked_to_previous = False
hp = header.paragraphs[0]
hp.paragraph_format.space_after = Pt(0)
hp.paragraph_format.tab_stops.add_tab_stop(Cm(CONTENT_W), WD_TAB_ALIGNMENT.RIGHT)
r = hp.add_run("软件工程课堂笔记 9")
set_run_font(r, 8.5, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
hp.add_run("\t")
r = hp.add_run("性能优化 · 实时通信")
set_run_font(r, 8.5, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
set_paragraph_border(hp, "bottom", BORDER, 4, 2)

footer = section.footer
footer.is_linked_to_previous = False
fp = footer.paragraphs[0]
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
fp.paragraph_format.space_before = Pt(0)
r = fp.add_run("第 ")
set_run_font(r, 8.2, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
r = add_field(fp, "PAGE")
set_run_font(r, 8.2, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
r = fp.add_run(" 页 / 共 ")
set_run_font(r, 8.2, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
r = add_field(fp, "NUMPAGES")
set_run_font(r, 8.2, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
r = fp.add_run(" 页")
set_run_font(r, 8.2, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)

# first page is intentionally clean
section.first_page_header.paragraphs[0].text = ""
section.first_page_footer.paragraphs[0].text = ""


# ---------------------------------------------------------------------------
# high-level content helpers
# ---------------------------------------------------------------------------

def spacer(points=4):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = Pt(points)
    r = p.add_run(" ")
    set_run_font(r, 1, False, WHITE)


def body(text="", lead=None, after=4, first_line=False):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.4
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    if lead:
        r = p.add_run(lead)
        set_run_font(r, 10.5, True, TEAL_2)
    r = p.add_run(text)
    set_run_font(r, 10.5, False, INK)
    return p


def bullet(text="", lead=None, after=2.5):
    # Avoid the common "心跳：心跳：..." duplication when the caller passes
    # both a lead label and a sentence that already starts with that label.
    if lead and text.startswith(lead):
        text = text[len(lead):].lstrip("：。，； ")
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.82)
    p.paragraph_format.first_line_indent = Cm(-0.42)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.35
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r = p.add_run("•  ")
    set_run_font(r, 10.5, True, TEAL_2)
    if lead:
        r = p.add_run(lead)
        set_run_font(r, 10.5, True, TEAL_2)
    r = p.add_run(text)
    set_run_font(r, 10.5, False, INK)
    return p


def numbered(items):
    for idx, item in enumerate(items, 1):
        if isinstance(item, tuple):
            lead, text = item
        else:
            lead, text = None, item
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Cm(0.9)
        p.paragraph_format.first_line_indent = Cm(-0.6)
        p.paragraph_format.tab_stops.add_tab_stop(Cm(0.9))
        p.paragraph_format.space_after = Pt(2.5)
        p.paragraph_format.line_spacing = 1.35
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        r = p.add_run(f"{idx:02d}.")
        set_run_font(r, 9.5, True, TEAL_2, HEAD_FONT_CN, HEAD_FONT_CN)
        r = p.add_run("\t")
        set_run_font(r, 9.5, False, TEAL_2)
        if lead:
            r = p.add_run(lead)
            set_run_font(r, 10.5, True, TEAL_2)
        r = p.add_run(text)
        set_run_font(r, 10.5, False, INK)


def h1(text, new_page=True):
    p = doc.add_paragraph(text, style="Heading 1")
    p.paragraph_format.page_break_before = new_page
    p.paragraph_format.keep_with_next = True
    set_paragraph_border(p, "bottom", TEAL, 10, 4)
    return p


def h2(text):
    p = doc.add_paragraph(text, style="Heading 2")
    p.paragraph_format.left_indent = Cm(0.18)
    set_paragraph_border(p, "left", GOLD, 14, 6)
    return p


def section_label(text, en=""):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run("■ ")
    set_run_font(r, 8, True, GOLD, HEAD_FONT_CN, HEAD_FONT_CN)
    r = p.add_run(text)
    set_run_font(r, 14, True, TEAL, HEAD_FONT_CN, HEAD_FONT_CN)
    if en:
        r = p.add_run("  " + en)
        set_run_font(r, 8.5, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
        set_char_spacing(r, 22)
    return p


def table_cell_text(cell, text, size=8.8, bold=False, color=INK,
                    align=WD_ALIGN_PARAGRAPH.LEFT, line=1.15):
    cell.text = ""
    p = cell.paragraphs[0]
    p.alignment = align
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = line
    for i, line_text in enumerate(str(text).split("\n")):
        if i > 0:
            p = cell.add_paragraph()
            p.alignment = align
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = line
        r = p.add_run(line_text)
        set_run_font(r, size, bold, color, BODY_FONT_CN, BODY_FONT_CN)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER


def add_table(headers, rows, widths, caption, font_size=8.8, header_size=8.8,
              align=None, first_col_bold=False, inside_v=True):
    global TABLE_NO
    TABLE_NO += 1
    cap = doc.add_paragraph(style="Table Caption")
    cap.add_run(f"表 {TABLE_NO}  {caption}")
    cap.paragraph_format.keep_with_next = True

    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_table_layout(table, widths)
    set_table_borders(table, BORDER, 4, inside_v)
    table.rows[0].height = Cm(0.72)
    set_row_props(table.rows[0], header=True, height=0.72)

    for i, head in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.width = Cm(widths[i])
        shade_cell(cell, TEAL)
        set_cell_margins(cell, 45, 80, 45, 80)
        table_cell_text(cell, head, header_size, True, WHITE, WD_ALIGN_PARAGRAPH.CENTER)

    for ridx, row in enumerate(rows):
        tr = table.add_row()
        set_row_props(tr, header=False, height=0.55)
        for cidx, value in enumerate(row):
            cell = tr.cells[cidx]
            cell.width = Cm(widths[cidx])
            if ridx % 2 == 1:
                shade_cell(cell, LIGHT_2)
            set_cell_margins(cell, 45, 80, 45, 80)
            cell_align = WD_ALIGN_PARAGRAPH.LEFT
            if align:
                cell_align = align[cidx]
            table_cell_text(
                cell, value, font_size,
                bold=(first_col_bold and cidx == 0),
                color=INK, align=cell_align,
            )

    for i, width in enumerate(widths):
        table.columns[i].width = Cm(width)
    spacer(3)
    return table


def callout(label, text, tone="teal"):
    if tone == "gold":
        fill, accent, label_color = CREAM, GOLD, AMBER
    elif tone == "red":
        fill, accent, label_color = "FBEFEC", RED, RED
    else:
        fill, accent, label_color = LIGHT, TEAL_2, TEAL

    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_table_no_borders(table)
    set_table_layout(table, [CONTENT_W])
    cell = table.rows[0].cells[0]
    cell.width = Cm(CONTENT_W)
    shade_cell(cell, fill)
    set_cell_margins(cell, 150, 220, 150, 220)
    set_cell_border(
        cell,
        left={"val": "single", "sz": 24, "color": accent, "space": 0},
        top={"val": "nil"},
        bottom={"val": "nil"},
        right={"val": "nil"},
    )
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.15
    r = p.add_run(label)
    set_run_font(r, 8.5, True, label_color, HEAD_FONT_CN, HEAD_FONT_CN)
    set_char_spacing(r, 25)
    p = cell.add_paragraph()
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.35
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r = p.add_run(text)
    set_run_font(r, 9.5, False, INK)
    spacer(3)


def code_block(code, size=8.2):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_table_no_borders(table)
    set_table_layout(table, [CONTENT_W])
    cell = table.rows[0].cells[0]
    cell.width = Cm(CONTENT_W)
    shade_cell(cell, "F6F8FA")
    set_cell_margins(cell, 120, 180, 120, 180)
    set_cell_border(
        cell,
        left={"val": "single", "sz": 18, "color": TEAL_3, "space": 0},
        top={"val": "single", "sz": 4, "color": "DCE4E7", "space": 0},
        bottom={"val": "single", "sz": 4, "color": "DCE4E7", "space": 0},
        right={"val": "single", "sz": 4, "color": "DCE4E7", "space": 0},
    )
    cell.text = ""
    first = True
    for line_text in code.strip("\n").split("\n"):
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        first = False
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.0
        r = p.add_run(line_text if line_text else " ")
        set_run_font(r, size, False, "26343B", CODE_FONT, HEAD_FONT_CN)
    spacer(4)


def figure(filename, width_cm, caption, alt):
    global FIGURE_NO
    FIGURE_NO += 1
    path = ASSETS / filename
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.keep_together = True
    shape = p.add_run().add_picture(str(path), width=Cm(width_cm))
    doc_pr = shape._inline.docPr
    doc_pr.set("descr", alt)
    doc_pr.set("title", alt)
    cap = doc.add_paragraph(style="Figure Caption")
    cap.add_run(f"图 {FIGURE_NO}  {caption}")
    spacer(4)


def divider():
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(6)
    set_paragraph_border(p, "bottom", BORDER, 4, 1)


# ---------------------------------------------------------------------------
# cover
# ---------------------------------------------------------------------------

p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(0)
p.paragraph_format.line_spacing = Pt(22)

cover = doc.add_table(rows=1, cols=1)
cover.alignment = WD_TABLE_ALIGNMENT.CENTER
cover.autofit = False
set_table_no_borders(cover)
set_table_layout(cover, [CONTENT_W])
row = cover.rows[0]
row.height = Cm(7.4)
row.height_rule = WD_ROW_HEIGHT_RULE.EXACTLY
cell = row.cells[0]
cell.width = Cm(CONTENT_W)
shade_cell(cell, TEAL)
set_cell_margins(cell, 480, 540, 420, 540)

cell.text = ""
p = cell.paragraphs[0]
p.paragraph_format.space_after = Pt(16)
r = p.add_run("SOFTWARE ENGINEERING  /  CLASS NOTES 09")
set_run_font(r, 9, False, "BFD9D2", HEAD_FONT_CN, HEAD_FONT_CN)
set_char_spacing(r, 55)

p = cell.add_paragraph()
p.paragraph_format.space_after = Pt(8)
p.paragraph_format.line_spacing = 1.05
r = p.add_run("软件工程课堂笔记 9")
set_run_font(r, 29, True, WHITE, HEAD_FONT_CN, HEAD_FONT_CN)
set_char_spacing(r, 15)

p = cell.add_paragraph()
p.paragraph_format.space_before = Pt(2)
p.paragraph_format.space_after = Pt(12)
set_paragraph_border(p, "bottom", GOLD, 18, 2)

p = cell.add_paragraph()
p.paragraph_format.space_after = Pt(5)
r = p.add_run("秒杀页面提速 · 慢页面排查 · 在线实时聊天应用设计")
set_run_font(r, 12.5, False, "EAF4F0", HEAD_FONT_CN, HEAD_FONT_CN)

p = cell.add_paragraph()
p.paragraph_format.space_after = Pt(0)
r = p.add_run("从“让页面更快”到“让消息可靠地到达”")
set_run_font(r, 10, False, "BFD9D2", HEAD_FONT_CN, HEAD_FONT_CN)

spacer(10)

meta_rows = [
    ("课程", "软件工程"),
    ("案例", "知学伴 · 课程实时答疑与班级群聊"),
    ("版本", "v1.0（课程作业版）"),
    ("日期", "2026 年 11 月 23 日"),
    ("作者", "软件工程课程项目组"),
]
for key, value in meta_rows:
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(7)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(3.2))
    r = p.add_run(key)
    set_run_font(r, 9, True, GRAY, HEAD_FONT_CN, HEAD_FONT_CN)
    r = p.add_run("\t")
    set_run_font(r, 9, False, GRAY)
    r = p.add_run(value)
    set_run_font(r, 10, False, INK)

spacer(18)
p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(0)
r = p.add_run("“性能是设计出来的，不是补出来的；实时是同步出来的，不是连出来的。”")
set_run_font(r, 9.5, False, GRAY, HEAD_FONT_CN, HEAD_FONT_CN, italic=True)

doc.add_page_break()


# ---------------------------------------------------------------------------
# abstract + reading navigation
# ---------------------------------------------------------------------------

section_label("摘要", "ABSTRACT")

body(
    "本文整理软件工程课堂笔记 9 的三个主题：秒杀场景的前端提速、慢页面排查与实时聊天技术。"
    "第一部分把课堂要点转化为可执行的技术清单；第二部分回答四个待研究问题——慢页面定位工具、"
    "离线消息批量写入、多端消息同步、群发读/写扩散；第三部分完成课堂任务，为个性化学习系统设计"
    "“知学伴”课程实时答疑与班级群聊应用，覆盖场景、需求、架构、协议、数据模型、关键流程、"
    "可靠性、安全、性能与测试。"
)
p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(8)
r = p.add_run("关键词：")
set_run_font(r, 9.5, True, TEAL)
r = p.add_run("秒杀；前端性能；Web Vitals；WebSocket；消息同步；读扩散；写扩散")
set_run_font(r, 9.5, False, GRAY)

divider()

section_label("阅读导航", "READING MAP")
body(
    "如果时间有限，建议先读 1.1、2.2、3.3 与 3.6：它们分别对应“怎么快”“怎么写”“怎么连”“怎么保证到达”。"
    "准备答辩时，重点看 2.4 的扩散选型与 3.9 的测试、SLO 和灰度方案。"
)

nav_rows = [
    ("01", "课堂笔记梳理", "秒杀前端提速、慢页面排查、实时聊天技术"),
    ("02", "四个待研究问题", "工具矩阵、离线批量写入、多端同步、读/写扩散"),
    ("03", "课堂任务", "知学伴场景、需求、架构、协议、数据模型、关键流程"),
    ("04", "总结与附录", "结论、提速清单、排查模板、参考资料"),
]
add_table(
    ["", "章节", "你将读到"],
    nav_rows,
    [1.1, 3.6, 11.3],
    "阅读导航：四个部分之间的逻辑关系",
    font_size=9.2,
    header_size=8.8,
    align=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT],
    first_col_bold=True,
)

callout(
    "使用说明",
    "本文既可作为课堂笔记复习，也可直接作为课堂任务的方案文档。文中的性能指标与容量数字是课程项目的设计目标，"
    "不是线上实测结论；落地时应以压测和真实用户监控数据为准。",
    "gold",
)


# ---------------------------------------------------------------------------
# section 1
# ---------------------------------------------------------------------------

h1("一、课堂笔记梳理", new_page=True)
body(
    "课堂笔记 9 把三个看似分散的主题放在一起：秒杀页面的前端提速、慢页面排查、实时聊天技术。"
    "前两个主题解决“页面为什么慢、怎样变快”，第三个主题解决“消息怎样在用户之间可靠、及时地流动”。"
    "三者共同的底层方法是：先测量，再定位，最后用可回滚的工程手段优化。"
)

h2("1.1 秒杀场景的前端提速")
body(
    "秒杀页面的典型矛盾是：流量在极短时间内集中爆发，而用户对首屏和交互的耐心极低。"
    "前端优化的目标不是把每一个字节都压到最小，而是把首屏关键路径做短，把非关键资源延后，"
    "把可缓存的内容尽量前移。"
)
bullet("同一时刻大量用户打开同一页面，静态资源、接口和 CDN 回源都会被放大。", lead="流量峰值高：")
bullet("用户往往只关心“能不能抢、什么时候开始”，首屏白屏和按钮无响应会直接造成流失。", lead="等待敏感：")
bullet("商品图、价格、库存、倒计时、资格校验分别来自不同系统，任何一段慢都会拖累整体。", lead="链路长：")

add_table(
    ["技术", "核心做法", "为什么有效", "适用边界"],
    [
        (
            "动静分离",
            "HTML、CSS、JS、图片走 CDN 或 Nginx；接口只返回动态数据；静态域名不带 Cookie，文件名带内容指纹。",
            "静态资源可边缘缓存、并行下载；动态请求更短，缓存命中率更高。",
            "要处理缓存刷新、版本号、跨域与 Cookie 隔离；HTML 不宜长缓存。",
        ),
        (
            "图片压缩",
            "按屏幕宽度输出 srcset；优先 WebP/AVIF；质量 75~82；CDN 自动转码；写明宽高避免布局抖动。",
            "图片常占首屏字节数一半以上，压缩能同时减少下载、解码和内存占用。",
            "注意透明通道、文字清晰度和旧浏览器降级；不能为了体积牺牲商品细节。",
        ),
        (
            "懒加载",
            "首屏外图片用 IntersectionObserver；大表格用虚拟滚动；楼层、评价、推荐位分段请求。",
            "减少首屏请求数和主线程工作，把带宽留给关键资源。",
            "首屏关键图不能懒加载；必须预留尺寸，避免 CLS；SEO 场景要提供降级内容。",
        ),
    ],
    [2.2, 6.3, 4.4, 3.1],
    "三类基础提速技术：动静分离、图片压缩、懒加载",
    font_size=8.5,
    first_col_bold=True,
)

body(
    "它的关键不是“把文件放到另一个目录”，而是让静态资源拥有独立的缓存策略、域名和发布节奏。"
    "HTML 负责组织页面，通常短缓存甚至不缓存；JS/CSS/图片使用内容指纹，设置为长期缓存；"
    "接口只承担个性化数据。这样一次发布只需要刷新 HTML，旧资源仍然可以被缓存命中。",
    lead="动静分离。",
)
body(
    "它的重点不是无脑降质量。先按容器宽度输出合适尺寸，再选择现代格式，最后才调质量参数；"
    "同时设置 width/height 或 aspect-ratio，避免图片加载后把内容“顶”下去。对商品图，"
    "可以保留一张高质量主图，其余缩略图使用更低质量。",
    lead="图片压缩。",
)
body(
    "要区分“首屏外”和“首屏内”。首屏主图、价格、倒计时、抢购按钮必须优先加载；"
    "首屏外的推荐位、评价、长列表才延迟。大表格的懒加载优先用虚拟滚动，而不是无限下拉，"
    "否则 DOM 节点仍然会越堆越多。",
    lead="懒加载。",
)

add_table(
    ["优先级", "技术", "关键动作", "主要收益"],
    [
        ("P0", "CDN + 动静分离", "静态资源边缘缓存、内容指纹、Cookie-free 域名", "降低 TTFB 与回源，提升并发承载"),
        ("P0", "图片压缩与响应式", "WebP/AVIF、srcset、尺寸声明、CDN 转码", "直接减少首屏字节与解码耗时"),
        ("P0", "懒加载 / 虚拟列表", "首屏外延迟、长列表只渲染可视区", "减少首屏请求与主线程压力"),
        ("P1", "HTTP 缓存", "immutable、max-age、ETag、版本化 URL", "二次访问几乎零下载"),
        ("P1", "代码分割", "路由级拆分、tree shaking、按需加载", "降低首屏 JS 体积与解析时间"),
        ("P1", "预连接 / 预加载", "preconnect、dns-prefetch、preload 关键资源", "缩短关键链路握手与发现时间"),
        ("P2", "接口合并 / 缓存 / 降级", "聚合接口、短缓存、失败兜底", "减少请求数，避免接口拖垮首屏"),
        ("P2", "SSR / 边缘渲染 + 骨架屏", "首屏直出、流式渲染、骨架占位", "改善白屏与弱网体验"),
    ],
    [1.2, 3.8, 6.9, 4.1],
    "按优先级排列的前端提速技术清单",
    font_size=8.5,
    align=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT,
           WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT],
)

callout(
    "课堂要求",
    "梳理至少三种提高页面访问速度的技术。必答组合是：动静分离（含 CDN）、图片压缩、懒加载/虚拟列表；"
    "生产环境再叠加 HTTP 缓存、代码分割、预连接、接口合并与骨架屏。选择技术时先看瓶颈在“下载、解析还是渲染”，"
    "不要把所有手段一次性堆上去。",
    "gold",
)

body(
    "组合策略可以概括为：首屏只加载关键 CSS、首屏所需 JS 和首屏主图；其余图片、楼层、表格、埋点全部延后；"
    "静态资源长缓存，HTML 短缓存；接口能缓存的部分缓存，不能缓存的做限流和降级；弱网或失败时先出骨架屏，"
    "再重试，而不是让用户盯着白屏。"
)
body(
    "衡量指标以 Core Web Vitals 为主：LCP、INP、CLS，辅以 TTFB、首屏资源体积、请求数、缓存命中率和 JS 执行时间。"
    "课程项目建议把目标写成可验收的数字：4G 模拟下 LCP ≤ 2.5s、INP ≤ 200ms、CLS ≤ 0.1、"
    "首屏 JS（gzip）≤ 200KB、首屏图片 ≤ 150KB。数字不是承诺，必须用 Lighthouse 加真实设备复测。"
)

h2("1.2 慢页面排查：原则与症状")
body(
    "“页面慢”至少可能发生在六个阶段：DNS/TCP/TLS 握手、等待首字节（TTFB）、资源下载、HTML/CSS 解析、"
    "渲染与绘制、交互与脚本执行。如果不先判断时间花在哪一段，优化很容易变成“凭感觉压缩图片”。"
)
add_table(
    ["阶段", "典型表现", "先看什么"],
    [
        ("DNS / TCP / TLS", "首次打开慢，第二次明显变快", "DNS 解析、连接复用、TLS 版本、HTTP/2"),
        ("TTFB", "浏览器一直“等待服务器响应”", "服务端耗时、数据库、缓存、冷启动、网关排队"),
        ("资源下载", "HTML 很快，图片/JS 慢慢出现", "资源体积、并发数、CDN 命中、网络带宽"),
        ("解析与执行", "白屏后突然卡一下，或按钮迟迟不能点", "JS 体积、长任务、第三方脚本、hydration"),
        ("渲染与绘制", "滚动卡顿、布局跳动、动画掉帧", "DOM 数量、重排重绘、图片尺寸、字体"),
        ("交互", "点击无响应、输入延迟", "主线程占用、事件处理、接口等待、内存泄漏"),
    ],
    [3.0, 6.2, 6.8],
    "慢页面的六个阶段与典型症状",
    font_size=8.6,
    first_col_bold=True,
)
body(
    "排查要同时看两类数据：实验室数据（Lighthouse、DevTools、WebPageTest）可复现、可对比；"
    "真实用户数据（RUM、web-vitals、Sentry）能告诉你“哪些用户、哪些地区、哪些机型”真的慢。"
    "只看 Lighthouse 分数，容易优化了测试环境却漏掉线上长尾。"
)
body(
    "三条原则：先复现再优化，先分层再下钻，一次只改一个变量。复现时固定设备、网络、缓存状态和账号；"
    "分层时先看 Network 瀑布判断阶段，再用 Performance 看主线程；验证时保留修改前后的指标和回滚方案。"
)

h2("1.3 实时聊天技术")
body(
    "笔记里提到“Socket 和 Netty”，需要先厘清三者关系：Socket 是操作系统提供的传输层编程接口；"
    "WebSocket 是建立在 TCP 之上的应用层协议；Netty 是 Java 的事件驱动网络框架。三者不是并列关系。"
    "实时聊天的核心也不是“建立一个 WebSocket”，而是连接、消息状态和多端同步三件事。"
)

body("一条消息从发送到被对方看到，通常要经过八个环节：", lead="核心链路。")
numbered([
    ("连接：", "客户端通过 HTTPS/WSS 连接网关，完成身份认证与设备登记。"),
    ("发送：", "客户端生成 client_msg_id，把消息发给网关；网关只做鉴权、限流和转发。"),
    ("校验：", "聊天服务校验会话成员、禁言状态、消息大小和内容安全。"),
    ("定序：", "为会话分配递增 seq，保证同一会话内可排序；seq 允许空洞。"),
    ("落库：", "消息写入数据库，同时写 Outbox 事件；两者在同一事务中完成。"),
    ("确认：", "数据库提交后返回 ACK，告诉发送方“已接收”，而不是“已读”。"),
    ("分发：", "Relay 把事件投递到消息队列，消费者按会话路由到在线设备。"),
    ("同步：", "离线设备上线后按 last_seq 增量拉取；用户阅读后上报已读回执。"),
])

add_table(
    ["方案", "连接方式", "实时性", "服务端压力", "适用场景"],
    [
        ("短轮询", "客户端定时 HTTP 请求", "取决于轮询间隔，通常秒级", "请求数大，大量空轮询", "极简通知、兼容性优先"),
        ("长轮询", "请求挂起，直到有消息或超时", "较好", "占用连接与线程，扩容成本高", "旧浏览器、临时方案"),
        ("SSE", "服务器单向推送事件流", "好", "轻量，但只能服务端到客户端", "通知、进度、行情"),
        ("WebSocket", "HTTP Upgrade 后全双工", "好，毫秒到百毫秒", "需要连接管理、心跳、重连", "聊天、协作、游戏"),
        ("Socket.IO", "WebSocket + 降级与房间封装", "好", "依赖库与协议，排障成本略高", "快速做 MVP、需要房间/广播"),
        ("MQTT", "发布/订阅，轻量二进制", "好", "适合海量弱网设备", "IoT、移动端弱网"),
    ],
    [2.4, 5.2, 4.2, 4.1, 3.5],
    "常见实时通信方案对比",
    font_size=8.4,
    first_col_bold=True,
)

body("连接建立后，真正的难点在“维持”和“恢复”：", lead="连接维护。")
bullet("心跳：服务端每 25~30 秒发 ping，客户端 10 秒内回 pong；连续丢失 2~3 次判定断线。", lead="心跳：")
bullet("重连：指数退避 1s、2s、4s、8s……上限 30s，并加入随机抖动，避免大量客户端同时重连造成“重连风暴”。", lead="重连：")
bullet("恢复：重连时携带每个会话的 last_seq，只拉缺失消息；不要全量重拉历史。", lead="恢复：")
bullet("鉴权：使用短期票据或安全 Cookie，避免把长期 JWT 放在 URL 查询串里被日志记录。", lead="鉴权：")
bullet("体验：断线时显示“正在重连”，保留输入框草稿，恢复后自动补发未确认消息。", lead="体验：")

body("消息可靠性要靠“三件套”加“一个原则”：", lead="可靠性。")
bullet("client_msg_id：客户端生成，重试时保持不变，服务端唯一约束去重。", lead="client_msg_id：")
bullet("server_msg_id：服务端生成的全局唯一 ID，便于追踪、举报和幂等处理。", lead="server_msg_id：")
bullet("seq：同一会话内递增，客户端按 seq 排序和补拉，不依赖时间戳。", lead="seq：")
bullet("先落库再 ACK，先 ACK 再异步扩散；扩散失败可以重试，不能因为推送失败就让发送方以为发送失败。", lead="原则：")

body("高并发扩展遵循“连接层无状态、业务层无状态、存储层可分片”：", lead="扩展。")
bullet("网关多节点部署，连接信息放 Redis，任意节点都能路由；网关不承载业务逻辑。", lead="网关：")
bullet("消息按 conversation_id 分区，保证同一会话有序；消费者水平扩展。", lead="消息队列：")
bullet("消息表按会话哈希分片或按月分区，历史消息冷热分离。", lead="存储：")
bullet("大群采用读扩散或“在线只推信号、内容按需拉取”，避免一条消息写一万行。", lead="大群：")
bullet("对慢消费者做背压和限流，必要时丢弃非关键通知，优先保证消息主链路。", lead="背压：")

callout(
    "一句话总结",
    "实时聊天 = 连接管理 + 消息状态机 + 多端同步。WebSocket 只是入口；真正决定体验的是 ACK、seq、游标、"
    "离线补偿和扩散策略。",
    "teal",
)


# ---------------------------------------------------------------------------
# section 2
# ---------------------------------------------------------------------------

h1("二、四个待研究问题", new_page=True)
body(
    "这一部分把课堂上的“待研究”从一句话展开为可执行方案。四个问题分别对应：怎么定位慢、"
    "怎么批量写、怎么多端同步、怎么群发。每个问题都给出选型、关键步骤和边界条件，避免只给结论。"
)

h2("2.1 慢页面排查：工具矩阵与操作步骤")
body(
    "回答笔记中的问题：可以用哪些工具定位页面加载缓慢的原因？工具不是越多越好，关键是每件工具回答不同的问题。"
    "先用低成本工具判断方向，再用重工具下钻。"
)
add_table(
    ["工具", "主要看什么", "适用场景", "产出"],
    [
        ("Chrome DevTools · Network", "瀑布、TTFB、资源大小、优先级、缓存命中", "单页面快速定位", "慢资源列表与阶段耗时"),
        ("Chrome DevTools · Performance", "主线程长任务、JS 调用栈、帧率、布局抖动", "渲染/脚本慢", "火焰图与优化点"),
        ("Lighthouse", "LCP、INP、CLS、TBT、机会清单", "上线前审计", "分数、诊断与建议"),
        ("Coverage", "未使用的 JS/CSS 字节", "包体过大", "可删代码比例"),
        ("web-vitals + RUM", "真实用户 P75/P95、版本/地域/机型分布", "线上真实体验", "慢用户分群"),
        ("WebPageTest", "多地域、多网络、filmstrip、视频", "复现弱网/异地", "对比视频与瀑布"),
        ("APM / SkyWalking / Arthas", "服务端耗时、慢 SQL、GC、线程池", "TTFB 高", "服务端瓶颈"),
        ("Nginx / CDN 日志", "缓存命中、5xx、回源、带宽", "静态资源与边缘问题", "命中率与错误率"),
    ],
    [4.1, 5.7, 3.3, 2.9],
    "慢页面排查工具矩阵",
    font_size=8.4,
    first_col_bold=True,
)

body("推荐按五步走，避免“打开 Performance 就开始录”：", lead="五步排查法。")
numbered([
    ("复现：", "固定设备、网络（4G/弱网）、缓存状态、账号和页面版本，记录 URL 与时间。"),
    ("量化：", "先看 RUM 的 P75/P95，再用 Lighthouse 复现；区分“一直慢”“首次慢”“高峰慢”。"),
    ("分层：", "看 Network 瀑布，判断时间花在 DNS、TTFB、下载、解析还是渲染。"),
    ("定位：", "Performance 找长任务；Coverage 找未使用代码；APM 找服务端；日志找缓存和错误。"),
    ("验证：", "一次只改一个变量，做前后对比；记录指标、样本量和回滚方案。"),
])

add_table(
    ["症状", "常见根因", "下一步"],
    [
        ("首屏白屏久，TTFB 高", "服务端慢、数据库慢、冷启动、网关排队", "APM、慢 SQL、连接池、缓存命中"),
        ("HTML 很快但图片慢慢出现", "图片过大、未压缩、无尺寸、CDN 未命中", "WebP/AVIF、srcset、CDN、宽高声明"),
        ("滚动卡顿", "长列表全量渲染、大图解码、频繁重排", "虚拟列表、图片懒加载、减少 DOM"),
        ("点击无响应", "主线程长任务、第三方脚本、hydration", "Performance、代码分割、延后执行"),
        ("二次访问仍然慢", "文件名无指纹、缓存头错误、CDN 不缓存", "immutable、版本化 URL、缓存规则"),
        ("只有部分用户慢", "地域、运营商、机型、弱网、旧版本", "RUM 分群、WebPageTest 多地域"),
    ],
    [5.0, 6.4, 4.6],
    "常见慢页面症状、根因与下一步",
    font_size=8.5,
    first_col_bold=True,
)
body(
    "排查记录至少包含：页面 URL、版本、设备/系统/浏览器、网络类型、是否命中缓存、LCP/INP/CLS/TTFB、"
    "瀑布截图、主线程截图、服务端 Trace ID、结论与复测结果。模板见附录 B。"
)

h2("2.2 离线消息如何从单次写入改为批量写入")
body(
    "先把“离线”拆成两种，否则很容易把客户端问题和服务端问题混在一起。发送端离线，消息在客户端排队；"
    "接收端离线，消息在服务端等待投递。两者的批量点不同，取舍也不同。"
)
add_table(
    ["场景", "数据在哪里排队", "批量点", "关键风险"],
    [
        ("发送端离线", "客户端 Outbox（IndexedDB / SQLite / Room）", "网络恢复后批量上传", "本地丢失、重复上传、顺序错乱"),
        ("接收端离线", "服务端收件箱 / 未读表 + 推送", "消费端批量落库、批量更新未读", "重复、乱序、推送延迟"),
    ],
    [3.4, 6.3, 3.5, 4.0],
    "两种“离线”及其批量写入位置",
    font_size=8.5,
    first_col_bold=True,
)

body("批量的触发条件不要只按条数，推荐四个维度同时约束：", lead="触发策略。")
bullet("条数：100~500 条一批；小批延迟低，大批吞吐高。", lead="条数：")
bullet("时间：最多等待 200ms~1s；超过窗口立即 flush，避免用户感知“消息卡住”。", lead="时间：")
bullet("字节：单批不超过 1MB，避免一次事务过大、锁持有过久。", lead="字节：")
bullet("时机：应用切后台、网络即将断开、定时器到期时主动 flush。", lead="时机：")

body("服务端消费端可以用“批量插入 + 批量更新未读 + 提交后确认”的结构：", lead="服务端批处理。")
code_block(
    """
@KafkaListener(topics = "chat.message.persisted", containerFactory = "batchFactory")
public void onBatch(List<MessageEvent> events, Acknowledgment ack) {
    List<MessageEvent> valid = dedupeByMsgId(events);
    messageRepository.batchInsert(valid);   // 一条 SQL，多值 VALUES
    inboxRepository.batchUpsert(valid);     // 批量更新 last_seq / unread
    ack.acknowledge();                      // 事务提交后再确认
    // 失败：retryQueue.add(valid)，幂等重试
}
"""
)

p = body("它的重点不是“批量”，而是“可恢复”：", lead="客户端 Outbox。")
p.paragraph_format.page_break_before = True
bullet("本地表至少包含 client_msg_id、conv_id、payload、status、retry_count、created_at。", lead="字段：")
bullet("状态机 pending → sending → sent → failed；应用重启后把 sending 重新置为 pending。", lead="状态机：")
bullet("每批 50 条以内，服务端返回逐条结果；只有收到 ACK 才标记 sent，失败按退避重试。", lead="确认：")
bullet("同一会话按本地序号发送，跨会话可以并行；服务端按 seq 重新定序。", lead="顺序：")

add_table(
    ["调整", "收益", "代价"],
    [
        ("增大批量", "吞吐上升、事务次数下降", "首条延迟上升、失败重试成本上升"),
        ("拉长时间窗口", "合并率上升、写放大下降", "用户感知延迟上升"),
        ("单事务批量提交", "原子性更好、锁次数减少", "锁范围变大、回滚成本上升"),
        ("异步批量 + 立即 ACK", "发送体验好", "需要 Outbox/重试保证最终一致"),
    ],
    [4.2, 6.2, 5.6],
    "批量写入的取舍",
    font_size=8.5,
    first_col_bold=True,
)

callout(
    "结论",
    "在线单聊不要为了批量而批量。批量适合离线补发、历史回填、通知汇总；在线消息应单条落库，"
    "用连接池、组提交和流水线降低延迟。把 P99 发送延迟放在第一位，再用异步扩散保证吞吐。",
    "teal",
)

h2("2.3 多端实时连接与消息同步")
body(
    "多端不是“多开几个 WebSocket”这么简单。同一个账号在手机、平板、网页同时在线时，要解决四件事："
    "每个设备都能实时收、离线设备能补齐、已读状态能同步、重复消息能去重。"
)
add_table(
    ["数据表", "关键字段", "作用"],
    [
        ("chat_device_session", "device_id, user_id, platform, push_token, last_active_at", "设备登记、推送令牌、在线状态"),
        ("chat_member", "conv_id, user_id, last_read_seq, last_delivered_seq, unread_count", "会话成员、已读/送达游标、未读数"),
        ("chat_message", "msg_id, conv_id, seq, sender_id, client_msg_id, type, content", "消息主表与幂等键"),
        ("chat_receipt", "msg_id, user_id, device_id, delivered_at, read_at", "按设备记录送达与已读"),
        ("chat_sync_cursor", "user_id, device_id, conv_id, last_seq", "每台设备的增量同步位置"),
    ],
    [3.8, 7.1, 5.1],
    "多端同步涉及的核心数据",
    font_size=8.4,
    first_col_bold=True,
)


bullet("每台设备独立建立 WSS 连接，连接 ID 与 device_id 绑定；同一设备重复登录时踢掉旧连接。", lead="设备：")
bullet("Redis 维护 user_id → [gateway_id, conn_id] 的路由表；网关本地也保留连接句柄。", lead="路由：")
bullet("心跳失败、网络切换、Token 过期都会触发断线；重连时先恢复连接，再同步消息。", lead="断线：")

body("它的核心是“游标 + 序号”，不是“时间戳”：", lead="同步。")
numbered([
    ("定序：", "服务端为每个会话分配递增 seq；seq 允许空洞，客户端不假设连续。"),
    ("携带：", "消息携带 msg_id 与 seq；客户端按 seq 排序，不按客户端时间排序。"),
    ("拉取：", "设备上线时携带 cursors[]，服务端返回每个会话 after_seq 之后的消息，单批 ≤200 条或 1MB。"),
    ("补齐：", "客户端发现 seq 跳变，直接请求 after_seq；落后太多则先取会话快照，再拉最近消息。"),
    ("回执：", "客户端收到消息后 ACK delivered；用户阅读后上报 read。"),
    ("同步：", "服务端把已读事件同步给同账号其他设备；未读数 = last_seq - last_read_seq。"),
])

body(
    "建议采用“账号级已读”：手机读过的消息，电脑也标记为已读。服务端保存该账号在每个会话的"
    "最大已读 seq，并把 read.sync 事件推给其他在线设备；离线设备上线后通过游标同步。若产品坚持"
    "“每台设备独立未读”，则把已读游标放到设备维度，代价是用户要在每台设备上重复阅读。",
    lead="已读语义。",
)
body("多端场景的处理可以归纳为下表：")
bullet("手机已读、电脑未读：服务端把账号级 read_seq 推到电脑，电脑更新未读徽标。", lead="已读同步：")
bullet("设备离线 3 天：上线后按 cursor 增量拉取；超过保留窗口则走会话快照。", lead="离线补齐：")
bullet("同一条消息重复推送：客户端按 msg_id 去重，重复消息只更新状态，不重复插入。", lead="重复：")
bullet("两台设备同时发消息：各自 client_msg_id 不同，服务端按 seq 定序，不构成冲突。", lead="并发：")
bullet("Token 过期：服务端关闭连接并返回 4002，客户端刷新票据后带 cursor 重连。", lead="鉴权：")
body(
    "消息内容以服务端为准，客户端只做乐观展示：先插入一条 status=SENDING 的本地消息，收到 ACK 后用"
    "server_msg_id 和 seq 替换；失败则标记 FAILED，重试时复用同一个 client_msg_id。不要用“最后写入获胜”"
    "去合并消息正文，编辑、撤回等操作应作为新的版本事件处理。",
    lead="冲突处理。",
)

h2("2.4 群发：读扩散还是写扩散")
body(
    "读扩散和写扩散描述的是“消息什么时候复制给成员”。写扩散在发送时把消息写进每个成员的收件箱，"
    "读的时候只读自己的收件箱；读扩散只写一份群消息流，成员读的时候再按会话聚合。"
)
add_table(
    ["维度", "写扩散", "读扩散"],
    [
        ("写入", "每个成员一行，写放大 = 成员数", "只写一条群消息，写放大 ≈ 1"),
        ("读取", "读自己的收件箱，一次查询", "读群消息流，按成员/游标过滤"),
        ("存储", "成员数 × 消息数", "消息数"),
        ("延迟", "写入时分发，读很快", "读时聚合，首屏可能更慢"),
        ("热点", "大群写入爆炸", "大群读放大"),
        ("适用", "小群、强未读、强实时", "超大群、公告、弱未读"),
    ],
    [2.8, 6.6, 6.6],
    "读扩散与写扩散的对比",
    font_size=8.6,
    first_col_bold=True,
)

body(
    "算一笔账：一个 10,000 人的群，每天 1,000 条消息，假设每条消息平均 200B。写扩散每天产生 1,000 万行，"
    "约 2GB 原始数据；读扩散每天只产生 1,000 行，约 0.2MB。若群只有 500 人，写扩散每天 50 万行，"
    "约 100MB，仍然可控。这个数量级差异，就是大群必须读扩散的原因。",
    lead="数量级。",
)

add_table(
    ["群规模 / 类型", "推荐策略", "理由"],
    [
        ("≤500 人的班级群", "写扩散", "成员少，未读与实时性实现简单，读路径最短"),
        ("500~5,000 人的课程群", "写扩散 + 读扩散兜底", "活跃成员写，沉默成员按需拉，兼顾成本与体验"),
        (">5,000 人的公告 / 万人群", "读扩散", "避免一条消息写数万行；在线只推“有新消息”信号"),
        ("系统通知 / 课程公告", "读扩散 + 全局未读计数", "一份内容，所有成员共享，读时按游标取"),
    ],
    [5.3, 4.4, 6.3],
    "群发扩散策略选型",
    font_size=8.6,
    first_col_bold=True,
)

body(
    "推荐“混合扩散”：小群写扩散，大群读扩散，边界按成员数和活跃度动态调整；在线成员收到轻量信号后"
    "主动拉取，离线成员只更新未读计数，不逐条推送内容。这样既避免写放大，也避免把消息内容广播给"
    "不需要的离线设备。",
    lead="混合策略。",
)
body("落地时注意四点：", lead="实现要点。")
bullet("消费者按 conversation_id 分区，批量写收件箱；失败进入重试队列，不能阻塞主消息链路。", lead="Fanout：")
bullet("大群维护 group_seq 和成员 last_read_seq，未读数用 Redis 计数，定期与数据库对账。", lead="未读：")
bullet("只推 message.signal，客户端按 after_seq 拉取；离线只更新未读，避免推送风暴。", lead="在线：")
bullet("公告、课程通知走读扩散；班级讨论走写扩散；两者在会话类型上区分，不要用一套策略硬套。", lead="分流：")

callout(
    "结论",
    "没有绝对正确的扩散方式，只有与群规模、活跃度和成本约束匹配的方式。小群优先写扩散，大群优先读扩散，"
    "中间地带用混合策略；在线推信号、离线拉内容，是超大群最实用的组合。",
    "teal",
)


# ---------------------------------------------------------------------------
# section 3
# ---------------------------------------------------------------------------

h1("三、课堂任务：知学伴实时答疑与班级群聊", new_page=True)
body(
    "课堂任务要求：选定应用场景，设计与该场景相匹配的在线实时聊天应用。本文选择“知学伴”——"
    "在现有个性化资源生成与学习多智能体系统上，增加课程实时答疑与班级群聊能力。"
    "它不是通用社交 IM，而是围绕课程、知识点的教学沟通工具。"
)

h2("3.1 场景选择与设计目标")
body(
    "为什么选这个场景？因为学习过程天然同时需要“实时”和“异步”：课前班级群通知、课中教师答疑、"
    "课后同学讨论、考前一对一追问；消息里会出现公式、代码、题目截图、资源卡片，普通聊天工具很难承载。"
    "现有系统已经有课程、资源、画像和 AI 助手，聊天可以直接挂接这些上下文。"
)
bullet("学生：随时提问、看班级通知、和同学讨论、接收 AI 生成的解释与资源卡片。", lead="学生：")
bullet("教师/助教：答疑、发公告、批注资源、管理群成员与禁言。", lead="教师/助教：")
bullet("管理员：课程群开通、举报处理、内容审计、容量与风险管理。", lead="管理员：")
bullet("作为群内机器人，提供知识点解释、资源推荐和常见问题汇总。", lead="AI 助手：")

body("要明确，否则很容易做成“什么都能聊”的通用 IM：", lead="边界。")
bullet("做：课程群、师生 1:1、资源卡片、题目讨论、公告、答疑记录。", lead="做：")
bullet("不做：陌生人社交、朋友圈、音视频会议、复杂电商交易。", lead="不做：")
bullet("一期先做文本、图片、代码块、资源卡片；语音、视频、端到端加密放到后续版本。", lead="分期：")

add_table(
    ["角色", "核心诉求", "关键权限"],
    [
        ("学生", "提问、讨论、看通知、收资源", "发消息、上传图片、引用、@、举报"),
        ("教师 / 助教", "答疑、公告、管理课堂", "发公告、置顶、禁言、撤回、查看答疑统计"),
        ("管理员", "课程群治理与合规", "建群/解散、成员管理、举报处理、审计导出"),
        ("AI 助手", "降低重复答疑成本", "被 @ 时回答、推荐资源、标记待人工处理"),
    ],
    [3.1, 6.7, 6.2],
    "知学伴的角色与权限",
    font_size=8.6,
    first_col_bold=True,
)

body("按优先级排列如下：", lead="用例。")
add_table(
    ["优先级", "用例", "说明"],
    [
        ("P0", "班级群文字聊天", "课程群内实时收发、历史消息、未读数"),
        ("P0", "师生 1:1 答疑", "学生私聊教师/助教，支持引用题目与截图"),
        ("P0", "离线消息与多端同步", "手机、网页切换后不丢消息、不重复"),
        ("P1", "图片 / 代码 / 公式消息", "题目截图、代码块、LaTeX 公式渲染"),
        ("P1", "回复、@、置顶、公告", "把讨论串起来，重要信息不沉底"),
        ("P1", "已读回执与未读徽标", "教师知道哪些学生看过公告"),
        ("P2", "AI 助手与资源卡片", "群内 @AI 答疑，推送课程资源卡片"),
        ("P2", "举报、禁言、审计", "课程群治理与合规"),
    ],
    [1.6, 6.2, 8.2],
    "知学伴的核心用例",
    font_size=8.5,
    align=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT],
)

body("设计原则可以概括为四条：教学优先、多端一致、可靠先于炫技、可渐进演进。")
bullet("消息组织围绕课程与知识点，而不是纯时间流；资源卡片、题目、解析可以互相引用。", lead="教学优先：")
bullet("同一账号的多台设备看到一致的会话、已读和未读，不因切换设备而“消息消失”。", lead="多端一致：")
bullet("先保证不丢、不重、可补拉，再考虑表情、特效、音视频。", lead="可靠优先：")
bullet("一期复用现有 Spring Boot、Vue3、PostgreSQL、Redis、RabbitMQ；消息量上来后再引入 Kafka 分区。", lead="可演进：")

h2("3.2 需求定义")
body(
    "功能需求按“会话—消息—同步—治理”四组展开。优先级用 P0/P1/P2 表示：P0 是一期必须交付，"
    "P1 是完整体验，P2 是增强能力。"
)
add_table(
    ["模块", "需求", "优先级"],
    [
        ("会话", "班级群、课程群、师生 1:1；建群、加群、退群、成员角色", "P0"),
        ("消息", "文本、图片、文件、代码块、公式、资源卡片；发送、失败重试、撤回", "P0"),
        ("消息", "回复/引用、@成员、置顶、公告、消息搜索", "P1"),
        ("同步", "离线消息、多端同步、增量拉取、消息去重、顺序保证", "P0"),
        ("状态", "未读数、已读回执、在线状态、正在输入（可选）", "P1"),
        ("通知", "站内通知、浏览器通知、移动端推送；免打扰", "P1"),
        ("AI", "@AI 助手答疑、资源推荐、常见问题汇总", "P2"),
        ("治理", "举报、禁言、敏感词/图片审核、审计日志", "P1"),
        ("管理", "课程群开通、成员导入、数据统计、容量监控", "P1"),
        ("体验", "草稿、断线重连提示、失败重发、无障碍与键盘操作", "P1"),
    ],
    [2.2, 11.8, 2.0],
    "功能需求清单",
    font_size=8.5,
    align=[WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER],
)

doc.add_page_break()
add_table(
    ["维度", "设计目标", "验证方式"],
    [
        ("并发", "10,000 在线连接；峰值 2,000 消息/秒", "压测：连接数、消息速率、错误率"),
        ("延迟", "同地域 P95 发送到接收 ≤ 200ms；P99 ≤ 500ms", "埋点：send_ts、deliver_ts"),
        ("可靠性", "消息不丢、不重；单会话有序", "故障注入、重复/丢失对账"),
        ("可用性", "核心聊天 99.95%；单节点故障不中断", "多副本、混沌测试"),
        ("恢复", "断线重连 ≤ 3s；增量补齐不全量重拉", "弱网、切网、杀进程测试"),
        ("容量", "消息保留 2 年；图片/文件对象存储", "容量评估、归档演练"),
        ("安全", "TLS、鉴权、会话权限、内容审核、审计", "渗透测试、权限矩阵"),
        ("体验", "首屏 ≤ 1.5s（4G）；滚动 60fps；内存 ≤ 150MB", "Lighthouse、真机、Performance"),
    ],
    [2.3, 8.2, 5.5],
    "非功能需求与验证方式",
    font_size=8.4,
    first_col_bold=True,
)
body(
    "约束：复用现有 Java 21 + Spring Boot 3.3 + Vue3 技术栈；数据库以 PostgreSQL 为主；"
    "Redis 负责路由、在线状态、seq 与未读计数；一期消息总线复用 RabbitMQ，目标态引入 Kafka 做消息日志与分区消费；"
    "图片/文件放对象存储，消息里只传签名 URL。"
)

h2("3.3 总体架构")
body(
    "架构采用“连接层与业务层分离”的方式：Netty 网关只负责 WebSocket 连接、心跳、鉴权、限流和转发；"
    "聊天业务由 Spring Boot 服务处理；消息持久化、事件投递、在线路由和推送各自独立。"
)
figure(
    "architecture.png",
    16.0,
    "实时聊天系统总体架构（目标态，课程项目可按规模裁剪）",
    "分层架构图：客户端、接入层、业务层、消息与缓存、存储层、可观测层。",
)

p = body("各层职责如下：")
p.paragraph_format.keep_with_next = True
bullet("客户端：Vue3 页面、移动 H5/小程序；负责本地缓存、乐观 UI、断线重连和增量同步。", lead="客户端：")
bullet("接入层：SLB/Nginx 终止 TLS、承载静态资源；Netty 网关管理长连接；短期票据与设备绑定。", lead="接入层：")
bullet("业务层：聊天、会话/群组、在线状态、同步、推送、审核、AI 助手、管理后台。", lead="业务层：")
bullet("Kafka 记录消息事件并按会话分区；Redis 存路由、在线、seq、未读；RabbitMQ 跑推送任务。", lead="消息/缓存：")
bullet("PostgreSQL 存消息、成员、回执、Outbox；对象存储放图片/文件；审计日志按需留存。", lead="存储层：")

body(
    "为什么网关不直接做业务？长连接网关的瓶颈在连接数、内存和网络 I/O，业务服务的瓶颈在数据库、锁和 LLM 调用，"
    "两者扩容节奏完全不同。把网关做薄，才能独立扩容、独立发布、独立降级；网关重启时，客户端重连到其他节点，"
    "业务不受影响。",
    lead="连接与业务分离。",
)
body(
    "容量估算（课程项目目标值，需压测校正）：单网关节点 4C8G 承载 5,000~10,000 连接，按 3 节点部署留出故障余量；"
    "聊天服务 3 副本；Kafka 12 分区，按 conversation_id 分区；PostgreSQL 主备 + 只读副本；Redis 3 主 3 从。"
    "若按 10,000 在线、10% 同时发消息、每条 500B 估算，入口带宽约 5Mbps，真正的压力在数据库写入与 fanout，"
    "不在带宽本身。",
    lead="部署与容量。",
)

h2("3.4 协议与接口设计")
body(
    "连接地址使用 wss://api.example.com/ws/chat，浏览器先通过 HTTPS 换取 60 秒有效的一次性 ticket，"
    "再建立 WebSocket；也可以使用安全 Cookie。不要把长期 JWT 放在 URL 查询串里，避免被代理和访问日志记录。"
    "连接建立后，客户端发送 auth 帧，服务端返回 auth.ok 和服务器时间。"
)
body("所有控制帧使用统一信封，便于版本演进和问题追踪：", lead="消息信封。")
code_block(
    """
{
  "v": 1,
  "type": "message.send",
  "requestId": "req_01J8...",
  "ts": 1763865600000,
  "payload": {
    "convId": "c_10086",
    "clientMsgId": "2f1c9e0a-...",
    "msgType": "text",
    "content": { "text": "这道题的边界条件怎么判断？" }
  }
}
"""
)

add_table(
    ["客户端 → 服务端", "payload 关键字段", "服务端响应"],
    [
        ("auth", "ticket, deviceId, platform, appVersion", "auth.ok / error 4001"),
        ("ping", "clientTime", "pong"),
        ("message.send", "convId, clientMsgId, msgType, content, replyTo", "message.ack / error"),
        ("message.read", "convId, seq", "read.ok"),
        ("sync.pull", "cursors: [{convId, lastSeq}], limit", "sync.batch / sync.done"),
        ("conversation.join", "convId", "conversation.joined"),
        ("typing", "convId, state", "无（可广播给会话成员）"),
    ],
    [3.8, 7.4, 4.8],
    "客户端到服务端的主要消息类型",
    font_size=8.4,
    first_col_bold=True,
)
add_table(
    ["服务端 → 客户端", "payload 关键字段", "说明"],
    [
        ("auth.ok", "userId, serverTime, heartbeatInterval", "认证成功，进入在线状态"),
        ("message.new", "msgId, convId, seq, senderId, msgType, content, createdAt", "新消息到达"),
        ("message.ack", "clientMsgId, msgId, seq, status", "发送成功，客户端替换本地消息"),
        ("message.read", "convId, userId, seq", "已读同步（同账号多端或会话成员）"),
        ("sync.batch", "convId, messages[], hasMore", "增量同步一批消息"),
        ("error", "code, message, requestId", "可重试或不可重试错误"),
    ],
    [3.6, 8.0, 4.4],
    "服务端到客户端的主要消息类型",
    font_size=8.4,
    first_col_bold=True,
)

body("一次发送与确认的最小交互如下：", lead="交互示例。")
code_block(
    """
// 客户端 → 服务端
{"v":1,"type":"message.send","requestId":"req_001","payload":{
  "convId":"c_10086","clientMsgId":"2f1c9e0a","msgType":"text",
  "content":{"text":"这道题的边界条件怎么判断？"}}}

// 服务端 → 客户端
{"v":1,"type":"message.ack","requestId":"req_001","payload":{
  "clientMsgId":"2f1c9e0a","msgId":"1839200011223344","seq":10241,"status":"SENT"}}
"""
)

add_table(
    ["错误码 / 关闭码", "含义", "客户端处理"],
    [
        ("4001", "认证失败", "清除本地票据，重新登录"),
        ("4002", "票据过期", "刷新 ticket 后重连，带 cursor"),
        ("4003", "触发限流", "退避重试，提示“发送过于频繁”"),
        ("4004", "无会话权限", "刷新会话列表，禁止重试"),
        ("4005", "会话不存在", "从本地列表移除，提示用户"),
        ("1013", "服务端过载", "指数退避重连，切换备用网关"),
    ],
    [3.4, 5.7, 6.9],
    "协议错误码与关闭码",
    font_size=8.5,
    first_col_bold=True,
)

body("有五个要点：", lead="设计要点。")
bullet("版本号 v 放在信封里，新增字段保持向后兼容；不要靠 URL 或 Header 猜版本。", lead="版本：")
bullet("requestId 用于请求响应配对和全链路追踪；日志里必须带 requestId、convId、msgId。", lead="追踪：")
bullet("clientMsgId 是幂等键，重试必须复用；服务端用唯一索引兜底。", lead="幂等：")
bullet("控制帧限制 64KB；图片/文件先走 HTTP 上传到对象存储，消息只带 URL 和元数据。", lead="大小：")
bullet("错误码区分“可重试”和“不可重试”，客户端不要对所有错误都无限重连。", lead="错误：")

h2("3.5 数据模型")
body(
    "数据模型围绕“会话—成员—消息—回执—同步游标”展开。消息表只存一份，不为每个成员复制；"
    "未读和游标放在成员表与同步表，读写路径分开。"
)
add_table(
    ["表", "关键列", "说明"],
    [
        ("chat_conversation", "id, type, course_id, title, owner_id, last_seq, created_at", "会话主表；type=1:1/group"),
        ("chat_member", "conv_id, user_id, role, last_read_seq, last_delivered_seq, unread_count, muted", "成员、角色、游标、免打扰"),
        ("chat_message", "id, conv_id, seq, sender_id, client_msg_id, type, content, status, created_at", "消息主表；content 用 JSONB"),
        ("chat_receipt", "msg_id, user_id, device_id, delivered_at, read_at", "按设备记录送达/已读"),
        ("chat_device_session", "device_id, user_id, platform, push_token, last_active_at", "设备与推送令牌"),
        ("chat_sync_cursor", "user_id, device_id, conv_id, last_seq", "每台设备的增量游标"),
        ("chat_outbox", "id, aggregate_id, event_type, payload, status, retry_count, next_retry_at", "事务性 Outbox"),
    ],
    [3.5, 8.4, 4.1],
    "核心数据表",
    font_size=8.2,
    first_col_bold=True,
)

add_table(
    ["表", "索引 / 分区", "用途"],
    [
        ("chat_message", "UNIQUE (conv_id, seq)", "会话内排序与分页"),
        ("chat_message", "UNIQUE (sender_id, client_msg_id)", "发送幂等"),
        ("chat_message", "INDEX (conv_id, created_at DESC)", "历史消息与搜索范围"),
        ("chat_member", "PRIMARY KEY (conv_id, user_id)", "成员校验"),
        ("chat_member", "INDEX (user_id, updated_at DESC)", "会话列表"),
        ("chat_outbox", "INDEX (status, next_retry_at)", "失败重试扫描"),
    ],
    [3.5, 7.1, 5.4],
    "关键索引与分区策略",
    font_size=8.4,
    first_col_bold=True,
)
body(
    "消息表按 conv_id 哈希分片，保证同一会话落在同一分片；历史数据按月归档到冷存储。"
    "content 使用 JSONB，既保留不同消息类型的差异，又避免为每种类型建一张表。"
)
code_block(
    """
// 文本
{"text":"这道题的边界条件怎么判断？"}
// 图片（先上传对象存储，再发消息）
{"url":"https://cdn.example.com/chat/2026/11/abc.webp","w":1280,"h":720,"thumb":"..."}
// 代码块
{"lang":"java","code":"if (n <= 0) return 0;","title":"边界判断"}
// 资源卡片
{"resourceId":"r_1024","title":"动态规划入门","cover":"...","url":"/resources/r_1024"}
"""
)
body(
    "消息标识用“三件套”：client_msg_id 负责幂等，server_msg_id（表中列名 msg_id）负责全局追踪，seq 负责会话内排序。"
    "三者缺一不可：只有 client_msg_id 无法全局检索；只有 server_msg_id 无法去重；只有 seq 无法跨会话定位。",
    lead="标识设计。",
)
body(
    "存储估算：按日均 200 万条、平均 400B 估算，消息表约 0.8GB/天；含索引和回执按 2GB/天预留，"
    "一年约 700GB；图片/文件走对象存储，按实际容量规划。实际项目应按课程数量、活跃群数和消息类型重新测算。",
    lead="容量。",
)

h2("3.6 关键流程")
body(
    "下图把“发送—落库—确认—扩散—回执”串起来。关键顺序是：先在同一事务里写消息和 Outbox，"
    "提交后再 ACK；Relay 异步投递，消费者 fanout；接收端 ACK 后更新回执。"
)
figure(
    "message-sequence.png",
    16.0,
    "单条消息从发送到送达的时序（含 Outbox 与回执）",
    "时序图：发送端、WSS 网关、聊天服务、Redis、PostgreSQL/Kafka、接收端之间的 16 步交互。",
)

body("可以拆成八步：", lead="主链路。")
numbered([
    ("发送：", "客户端生成 clientMsgId，发送 message.send；本地先展示 SENDING。"),
    ("转发：", "网关校验票据、限流，把帧转给聊天服务，附带 userId、deviceId、网关 ID。"),
    ("校验：", "聊天服务校验会话成员、禁言、消息大小与内容安全。"),
    ("定序：", "INCR 会话 seq；seq 允许空洞，客户端按 > lastSeq 拉取。"),
    ("落库：", "同一事务插入 chat_message 和 chat_outbox；client_msg_id 唯一约束防重。"),
    ("确认：", "事务提交后返回 message.ack；客户端把本地消息标记 SENT。"),
    ("扩散：", "Relay 扫描 Outbox，投递到 Kafka；消费者按会话查在线路由。"),
    ("送达：", "在线设备收 message.new 并 ACK；服务端更新回执和未读，离线设备走推送/补拉。"),
])

body("发送服务的核心代码可以简化为：", lead="伪代码。")
code_block(
    """
@Transactional
public SendResult send(Long userId, SendCommand cmd) {
    assertMember(userId, cmd.convId());                 // 会话权限
    assertNotMuted(userId, cmd.convId());               // 禁言/限流
    long seq = redis.incr("conv:seq:" + cmd.convId());  // 会话内定序
    ChatMessage msg = messageRepo.insert(               // 唯一键防重
        userId, cmd, seq, MessageStatus.SENT);
    outboxRepo.insert("MESSAGE_PERSISTED", msg);        // 同事务写事件
    return new SendResult(msg.id(), seq);               // 提交后由上层 ACK
}
"""
)
body(
    "为什么先 ACK 再扩散？因为发送方关心的是“服务端是否可靠接收”，不是“对方是否已经看到”。"
    "扩散可能因为对方离线、网关重启、队列积压而延迟；如果等扩散完成才 ACK，会把下游的不稳定传导给发送方。"
    "正确做法是：落库成功即 ACK；扩散由 Outbox/队列保证最终完成；接收端再回 delivered/read 回执。",
    lead="顺序。",
)

body("异常路径要提前设计，不能只测 happy path：", lead="失败处理。")
bullet("数据库失败：不 ACK，客户端保留 SENDING，按 clientMsgId 重试；服务端唯一约束防重。", lead="DB 失败：")
bullet("Outbox 投递失败：Relay 重试并告警；消息已落库，发送方仍看到成功。", lead="队列失败：")
bullet("网关宕机：客户端重连到其他节点，带 cursor 补拉；连接路由由 Redis 重建。", lead="网关宕机：")
bullet("接收端离线：写离线收件箱/未读，触发厂商推送；上线后按 lastSeq 拉取。", lead="接收端离线：")
bullet("重复消费：消费者按 msgId 幂等，重复消息只更新回执，不重复插入。", lead="重复：")

h2("3.7 前端实现与性能")
body(
    "课堂笔记里的前端提速手段，在聊天页同样适用，只是优化对象从“商品图”变成“消息列表、图片和长历史”。"
    "聊天页最常见的三个性能问题是：首屏包过大、历史消息一次性渲染、图片附件解码卡顿。"
)
add_table(
    ["问题", "手段", "观察指标"],
    [
        ("首屏包大", "路由级代码分割、按需加载表情/编辑器、tree shaking", "首屏 JS 体积、LCP"),
        ("历史消息多", "虚拟列表、分页拉取、只渲染可视区、保留滚动位置", "滚动帧率、DOM 数、内存"),
        ("图片附件", "缩略图、WebP/AVIF、懒加载、点击后再加载原图", "图片解码时间、流量"),
        ("频繁重渲染", "消息增量追加、不可变数据、批量更新、避免整列表重排", "提交次数、长任务"),
        ("切网/重连", "本地缓存、断线提示、增量补拉、失败重发", "重连耗时、重复率"),
        ("弱网白屏", "骨架屏、IndexedDB 缓存最近会话、离线草稿", "首屏可见时间"),
    ],
    [3.3, 8.7, 4.0],
    "聊天页的前端性能问题与手段",
    font_size=8.4,
    first_col_bold=True,
)
body("聊天页的慢页面排查，优先看四处：", lead="排查清单。")
bullet("首屏 JS 是否把表情、代码高亮、Markdown 编辑器都打进了主包。", lead="包体：")
bullet("历史消息是否一次性插入几千个 DOM 节点；滚动时是否不断重排。", lead="渲染：")
bullet("图片是否按原图加载；是否缺少宽高导致 CLS。", lead="图片：")
bullet("WebSocket 消息是否每条都触发整个会话列表重渲染。", lead="状态：")
body(
    "4G 下首屏 ≤ 1.5s，消息列表滚动 60fps，连续使用 30 分钟内存 ≤ 150MB，断线重连 ≤ 3s。"
    "这些指标要放进前端监控，而不是只在开发机上看一眼。",
    lead="体验目标。",
)

h2("3.8 安全、审核与隐私")
body(
    "教育场景的聊天既有普通 IM 的风险，也有未成年人保护、课程内容版权和考试诚信等特殊要求。"
    "安全设计的原则是：默认不信任客户端、最小权限、传输与存储都加密、关键操作可审计。"
)
bullet("传输：全站 HTTPS/WSS，TLS 1.2+；票据短有效期、一次性、绑定设备。", lead="传输：")
bullet("鉴权：每次发消息都校验会话成员与角色；不能只依赖前端隐藏按钮。", lead="鉴权：")
bullet("内容：文本敏感词、图片审核、文件类型与大小限制；审核异步化，命中后先屏蔽再人工复核。", lead="内容：")
bullet("文件：对象存储私有桶 + 签名 URL；禁止把可执行文件当图片上传；病毒扫描。", lead="文件：")
bullet("隐私：最少化收集；支持撤回、删除、导出；审计日志脱敏；遵守学校与平台的数据留存要求。", lead="隐私：")
bullet("防滥用：发送频率限制、@全体成员权限、公告权限、举报与禁言。", lead="防滥用：")

h2("3.9 测试、监控与上线")
body(
    "实时系统的测试重点不是“功能点是否点得动”，而是“异常和压力下是否仍然可靠”。"
    "测试矩阵至少覆盖协议、并发、故障、兼容和安全五类。"
)
add_table(
    ["类型", "内容", "工具 / 方法", "通过标准"],
    [
        ("单元测试", "定序、幂等、权限、消息状态机", "JUnit 5、Mockito", "核心分支覆盖率 ≥ 80%"),
        ("集成测试", "WebSocket 握手、发送、ACK、离线补拉", "Testcontainers、真实 PG/Redis", "主链路全通过"),
        ("协议测试", "非法帧、超大消息、重复 clientMsgId、乱序", "自定义压测客户端", "不崩溃、错误码正确"),
        ("压力测试", "10k 连接、2k msg/s、万人群 fanout", "Gatling / k6 / 自研", "P95/P99 达标、无积压"),
        ("故障测试", "杀网关、Redis 主从切换、Kafka 停 broker", "Chaos Mesh / 手动", "自动重连、消息不丢"),
        ("兼容测试", "Chrome/Safari/微信 WebView/低端机", "真机、BrowserStack", "首屏与滚动达标"),
        ("安全测试", "越权、重放、XSS、文件上传", "OWASP ZAP、手工", "无高危漏洞"),
    ],
    [2.4, 6.5, 4.4, 4.2],
    "测试矩阵",
    font_size=8.2,
    first_col_bold=True,
)

body("至少包含五个场景：", lead="压测。")
bullet("10,000 连接稳定保持 30 分钟，心跳与内存不泄漏。", lead="连接：")
bullet("2,000 消息/秒持续写入，P95 端到端 ≤ 200ms，数据库无锁等待尖峰。", lead="吞吐：")
bullet("10,000 人群发一条公告，观察 fanout 延迟与队列积压。", lead="大群：")
bullet("5,000 客户端同时重连，验证退避与网关限流，不出现重连风暴。", lead="重连：")
bullet("Kafka 停一个 broker、Redis 主从切换，消息不丢、重复可接受但可去重。", lead="故障：")

add_table(
    ["指标", "目标", "告警"],
    [
        ("连接成功率", "≥ 99.9%", "5 分钟低于 99% 告警"),
        ("发送到接收 P95", "≤ 200ms", "10 分钟高于 300ms 告警"),
        ("消息投递成功率", "≥ 99.99%", "5 分钟低于 99.9% 告警"),
        ("重复率", "≤ 0.01%", "按 msgId 对账，异常升高告警"),
        ("Kafka 积压", "≤ 10,000 条", "持续 5 分钟告警"),
        ("网关错误率", "≤ 0.1%", "5 分钟高于 0.5% 告警"),
    ],
    [4.4, 5.8, 5.8],
    "核心 SLO 与告警阈值（课程项目建议值）",
    font_size=8.5,
    first_col_bold=True,
)
body(
    "上线采用“灰度 + 可回滚”：先在一个课程群/一个租户开启，观察连接、延迟、错误率和举报量；"
    "再按 5%→25%→100% 扩大。保留旧的轮询/HTTP 兜底通道至少一个版本；出现严重问题时能按课程维度关闭聊天，"
    "不影响资源学习主链路。",
    lead="灰度。",
)

h2("3.10 演进路线与风险")
body(
    "课程项目不建议一次做全。按“能演示、能压测、能讲清楚取舍”的目标分三期，每期都有明确出口。"
)
add_table(
    ["阶段", "范围", "出口标准"],
    [
        ("一期（MVP）", "班级群 + 师生 1:1；文本/图片；WebSocket；离线补拉；基础审核", "两个课程群真实试用一周，无消息丢失"),
        ("二期", "多端同步、已读回执、@/回复/公告、推送、搜索、资源卡片", "三端切换不丢消息，P95 ≤ 300ms"),
        ("三期", "大群读扩散、AI 助手、万人群、多地域、审计与数据看板", "万人群压测通过，成本可控"),
    ],
    [3.2, 9.2, 3.6],
    "演进路线",
    font_size=8.4,
    first_col_bold=True,
)

body("主要风险与对策如下：")
bullet("大群写放大：按 2.4 的混合扩散，超过阈值切读扩散，在线只推信号。", lead="热点群：")
bullet("指数退避 + 抖动 + 网关限流 + 分批放行。", lead="重连风暴：")
bullet("消息存储膨胀：按月分区、冷热分离、图片走对象存储、保留策略。", lead="存储：")
bullet("内容审核误判：先屏蔽后复核，提供申诉；避免“一刀切”影响正常答疑。", lead="审核：")
bullet("厂商推送不稳定：WS 在线优先，推送只做提醒；失败进入重试队列。", lead="推送：")
bullet("一期明确不做音视频、陌生人社交、复杂已读统计。", lead="范围蔓延：")

body("清单如下（答辩前逐项确认）：", lead="验收。")
for item in (
    "两个浏览器/两台设备同时在线，消息双向可达。",
    "断网 30 秒后恢复，消息自动补发/补拉，不重复。",
    "手机关闭页面后重新打开，历史消息与未读数正确。",
    "同一账号手机已读，网页未读徽标同步消失。",
    "群发 500 人，P95 延迟与 fanout 时间可解释。",
    "重复提交同一个 clientMsgId，只产生一条消息。",
    "越权访问其他课程群被拒绝，错误码正确。",
    "图片上传走对象存储，消息体不超过限制。",
    "网关宕机后客户端 3 秒内重连到其他节点。",
    "核心指标有监控面板，告警能触发。",
):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.6)
    p.paragraph_format.first_line_indent = Cm(-0.45)
    p.paragraph_format.space_after = Pt(1.5)
    p.paragraph_format.line_spacing = 1.3
    r = p.add_run("□  ")
    set_run_font(r, 10, False, TEAL_2)
    r = p.add_run(item)
    set_run_font(r, 9.8, False, INK)


# ---------------------------------------------------------------------------
# section 4
# ---------------------------------------------------------------------------

h1("四、总结", new_page=True)
body(
    "这份笔记的三个主题最终指向同一个工程方法：先用指标定义问题，再用分层定位原因，最后用可回滚的方案验证。"
    "秒杀前端提速是把关键路径做短；慢页面排查是找到时间到底花在哪；实时聊天是把连接、消息状态和多端同步"
    "做成一个可恢复的系统。"
)
body(
    "如果只能记住三句话：第一，性能优化不是“把所有资源都压小”，而是让首屏关键路径最短、非关键资源延后；"
    "第二，实时聊天的难点不在 WebSocket，而在 ACK、seq、游标、离线补偿和幂等；第三，读扩散与写扩散没有"
    "绝对优劣，只有与群规模、活跃度和成本匹配的选择。"
)
callout(
    "三句话带走",
    "① 先测量，再优化；② 先落库，再 ACK，再扩散；③ 小群写扩散，大群读扩散，在线推信号、离线拉内容。",
    "teal",
)
body("建议按以下顺序讲，控制在 3~5 分钟：", lead="答辩要点。")
numbered([
    ("场景：", "为什么学习平台需要实时答疑，而不是继续用论坛/邮件。"),
    ("取舍：", "为什么选 WebSocket + Netty 网关，为什么连接层与业务层分离。"),
    ("可靠：", "client_msg_id、seq、Outbox、ACK、离线补拉如何共同保证不丢不重。"),
    ("扩展：", "小群写扩散、大群读扩散的边界与成本账。"),
    ("验证：", "压测、故障注入、SLO 与灰度方案，说明“怎么证明它能用”。"),
])


# ---------------------------------------------------------------------------
# appendix
# ---------------------------------------------------------------------------

h1("附录", new_page=True)
h2("附录 A  秒杀前端提速检查清单")
for item in (
    "首屏 HTML 是否短缓存/不缓存，静态资源是否带内容指纹并长缓存。",
    "静态资源是否走 CDN，是否使用 Cookie-free 域名，是否开启 HTTP/2 或 HTTP/3。",
    "首屏主图是否压缩为 WebP/AVIF，是否按容器宽度输出 srcset，是否声明宽高。",
    "首屏外图片/楼层是否懒加载，长列表是否使用虚拟滚动。",
    "首屏 JS 是否按路由拆分，是否移除未使用代码与第三方脚本。",
    "关键字体、CSS、首屏接口是否 preload/preconnect，是否避免阻塞渲染。",
    "接口是否合并/缓存/降级，失败是否有骨架屏和重试。",
    "是否用 Lighthouse + 真机 + RUM 记录优化前后的 LCP、INP、CLS、TTFB。",
):
    bullet(item)

h2("附录 B  慢页面排查记录模板")
add_table(
    ["字段", "记录内容"],
    [
        ("页面 / 版本", "URL、前端版本、后端版本、发布时间"),
        ("环境", "设备、系统、浏览器、网络（4G/Wi-Fi/弱网）、是否命中缓存"),
        ("指标", "LCP、INP、CLS、TTFB、首屏资源体积、请求数"),
        ("瀑布", "DNS/TCP/TLS、TTFB、下载、解析、渲染各阶段耗时"),
        ("主线程", "长任务、JS 调用栈、第三方脚本、hydration"),
        ("服务端", "Trace ID、慢 SQL、缓存命中、连接池、GC"),
        ("结论", "根因、修改项、预期收益、复测结果"),
    ],
    [4.0, 12.0],
    "慢页面排查记录模板",
    font_size=8.8,
    first_col_bold=True,
)

h2("附录 C  参考资料")
refs = [
    ("RFC 6455 — The WebSocket Protocol", "https://www.rfc-editor.org/rfc/rfc6455"),
    ("MDN — WebSocket API", "https://developer.mozilla.org/zh-CN/docs/Web/API/WebSockets_API"),
    ("web.dev — Core Web Vitals", "https://web.dev/articles/vitals"),
    ("Chrome DevTools — Performance", "https://developer.chrome.com/docs/devtools/performance"),
    ("Chrome DevTools — Lighthouse", "https://developer.chrome.com/docs/lighthouse/overview"),
    ("Socket.IO 官方文档", "https://socket.io/docs/v4/"),
    ("Netty 官方站点", "https://netty.io/"),
    ("Apache Kafka 文档", "https://kafka.apache.org/documentation/"),
    ("Redis 文档", "https://redis.io/docs/latest/"),
    ("WebPageTest", "https://www.webpagetest.org/"),
]
for idx, (name, url) in enumerate(refs, 1):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.7)
    p.paragraph_format.first_line_indent = Cm(-0.7)
    p.paragraph_format.space_after = Pt(0.5)
    r = p.add_run(f"[{idx}] ")
    set_run_font(r, 8.7, True, TEAL_2)
    r = p.add_run(name + "  ")
    set_run_font(r, 8.7, False, INK)
    r = p.add_run(url)
    set_run_font(r, 8.3, False, TEAL_2, CODE_FONT, HEAD_FONT_CN)

# ---------------------------------------------------------------------------
# save
# ---------------------------------------------------------------------------

OUT_DIR.mkdir(parents=True, exist_ok=True)
doc.save(str(OUT_DOCX))
print(f"saved: {OUT_DOCX}")
print(f"paragraphs={len(doc.paragraphs)} tables={len(doc.tables)} figures={FIGURE_NO}")
