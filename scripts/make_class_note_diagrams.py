# -*- coding: utf-8 -*-
"""Generate readable architecture and sequence diagrams for class notes 09.

The canvas is intentionally compact: the figures are inserted at 16 cm width in
Word, so a 1600 px canvas keeps 24-28 px labels at roughly 7-8 pt.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "课堂笔记9" / "assets"
OUT.mkdir(parents=True, exist_ok=True)

FONT_REG = "C:/Windows/Fonts/msyh.ttc"
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"

INK = "#1F2D33"
MUTED = "#5E6E75"
TEAL = "#174A5B"
TEAL_2 = "#2C6E7A"
TEAL_3 = "#3C8C8C"
GOLD = "#C49A3A"
AMBER = "#B7791F"
GREEN = "#2F7D5A"
LINE = "#C9D6DA"
LIGHT = "#F4F7F8"
WHITE = "#FFFFFF"


def font(size: int, bold: bool = False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(draw, box, fill, outline=None, radius=12, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def center_text(draw, box, lines, fnt, fill=INK, spacing=4):
    x1, y1, x2, y2 = box
    if isinstance(lines, str):
        lines = [lines]
    bboxes = [draw.textbbox((0, 0), line, font=fnt) for line in lines]
    heights = [b[3] - b[1] for b in bboxes]
    line_h = max(heights) if heights else 0
    total_h = line_h * len(lines) + spacing * (len(lines) - 1)
    y = (y1 + y2 - total_h) / 2
    for i, line in enumerate(lines):
        draw.text(((x1 + x2) / 2, y + i * (line_h + spacing)), line,
                  font=fnt, fill=fill, anchor="ma")


def arrow(draw, start, end, color, width=3, dashed=False, head=10):
    x1, y1 = start
    x2, y2 = end
    if dashed:
        dx, dy = x2 - x1, y2 - y1
        length = (dx * dx + dy * dy) ** 0.5
        if length == 0:
            return
        ux, uy = dx / length, dy / length
        pos = 0.0
        while pos < length - head:
            seg_end = min(pos + 9, length - head)
            draw.line((x1 + ux * pos, y1 + uy * pos,
                       x1 + ux * seg_end, y1 + uy * seg_end),
                      fill=color, width=width)
            pos += 16
    else:
        draw.line((x1, y1, x2, y2), fill=color, width=width)
    dx, dy = x2 - x1, y2 - y1
    length = (dx * dx + dy * dy) ** 0.5
    if length == 0:
        return
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    bx, by = x2 - ux * head, y2 - uy * head
    draw.polygon([(x2, y2),
                  (bx + px * head * 0.55, by + py * head * 0.55),
                  (bx - px * head * 0.55, by - py * head * 0.55)],
                 fill=color)


def dashed_vline(draw, x, y1, y2, color=LINE, width=2):
    y = y1
    while y < y2:
        draw.line((x, y, x, min(y + 7, y2)), fill=color, width=width)
        y += 13


def architecture():
    W, H = 1600, 1165
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)

    f_title = font(34, True)
    f_sub = font(20)
    f_layer = font(24, True)
    f_layer_en = font(14)
    f_box = font(27, True)
    f_box_small = font(25, True)
    f_note = font(20)

    d.text((50, 30), "实时聊天系统总体架构", font=f_title, fill=TEAL)
    d.text((50, 78), "接入层无状态  ·  业务层可扩展  ·  消息按 conversation_id 分区",
           font=f_sub, fill=MUTED)

    rows = [
        ("客户端", "CLIENT", 120, 135, "#EAF4F0"),
        ("接入层", "ACCESS", 285, 135, "#E7F1F4"),
        ("业务层", "SERVICE", 450, 235, "#F7F3E8"),
        ("消息 / 缓存", "MESSAGING", 715, 125, "#EEF4F1"),
        ("存储层", "STORAGE", 870, 125, "#F1F3F5"),
        ("可观测", "OBSERVE", 1025, 95, "#F4F0F6"),
    ]
    for cn, en, y, h, fill in rows:
        rounded(d, (40, y, 205, y + h), TEAL, None, radius=14)
        center_text(d, (40, y, 205, y + h), [cn, en], f_layer, WHITE, spacing=0)

    # client
    cy = 120
    boxes = [
        (225, cy, 654, cy + 105, "Web 端", "Vue 3 + Pinia"),
        (678, cy, 1107, cy + 105, "移动 H5 / 小程序", "响应式 WebView · 弱网重连"),
        (1131, cy, 1560, cy + 105, "桌面端（可选）", "Electron / Tauri"),
    ]
    for x1, y1, x2, y2, title, sub in boxes:
        rounded(d, (x1, y1, x2, y2), WHITE, LINE, radius=12, width=2)
        center_text(d, (x1, y1 + 10, x2, y1 + 56), [title], f_box, TEAL)
        center_text(d, (x1, y1 + 58, x2, y1 + 96), [sub], f_note, MUTED)

    # access
    ay = 285
    boxes = [
        (225, ay, 654, ay + 105, "SLB / Nginx", "TLS · 静态资源 · 限流"),
        (678, ay, 1107, ay + 105, "WSS 网关集群", "Netty · 连接 · 心跳 · 转发"),
        (1131, ay, 1560, ay + 105, "JWT / 票据", "短期票据 · 设备绑定"),
    ]
    for x1, y1, x2, y2, title, sub in boxes:
        rounded(d, (x1, y1, x2, y2), "#F9FCFD", TEAL_2, radius=12, width=2)
        center_text(d, (x1, y1 + 10, x2, y1 + 56), [title], f_box, TEAL)
        center_text(d, (x1, y1 + 58, x2, y1 + 96), [sub], f_note, MUTED)

    # business
    by = 450
    top = [
        (225, by, 543, by + 95, "聊天服务", "校验 · 落库 · ACK"),
        (563, by, 881, by + 95, "会话与群组", "成员 · 权限 · 禁言"),
        (901, by, 1219, by + 95, "在线状态", "路由 · 心跳"),
        (1239, by, 1560, by + 95, "同步服务", "游标 · 增量拉取"),
    ]
    bottom = [
        (225, by + 115, 543, by + 210, "推送服务", "厂商推送 · 收件箱"),
        (563, by + 115, 881, by + 210, "内容审核", "文本 · 图片 · 文件"),
        (901, by + 115, 1219, by + 210, "AI 学习助手", "答疑 · 资源卡片"),
        (1239, by + 115, 1560, by + 210, "管理后台", "群管理 · 审计"),
    ]
    for boxes in (top, bottom):
        for x1, y1, x2, y2, title, sub in boxes:
            rounded(d, (x1, y1, x2, y2), "#FFFDF7", GOLD, radius=10, width=2)
            center_text(d, (x1, y1 + 8, x2, y1 + 48), [title], f_box_small, "#6B4E16")
            center_text(d, (x1, y1 + 50, x2, y1 + 86), [sub], f_note, MUTED)

    # messaging
    my = 715
    boxes = [
        (225, my, 654, my + 95, "Kafka 消息日志", "message.persisted · 按会话分区"),
        (678, my, 1107, my + 95, "Redis", "路由 · 在线 · Seq · 未读"),
        (1131, my, 1560, my + 95, "RabbitMQ", "推送任务 · 重试队列"),
    ]
    for x1, y1, x2, y2, title, sub in boxes:
        rounded(d, (x1, y1, x2, y2), "#F7FBF9", GREEN, radius=12, width=2)
        center_text(d, (x1, y1 + 10, x2, y1 + 50), [title], f_box_small, "#225B43")
        center_text(d, (x1, y1 + 52, x2, y1 + 86), [sub], f_note, MUTED)

    # storage
    sy = 870
    boxes = [
        (225, sy, 654, sy + 95, "PostgreSQL", "消息 · 成员 · 回执 · Outbox"),
        (678, sy, 1107, sy + 95, "对象存储", "图片 · 文件 · 签名 URL"),
        (1131, sy, 1560, sy + 95, "审计日志（可选）", "举报 · 风控 · 合规留存"),
    ]
    for x1, y1, x2, y2, title, sub in boxes:
        rounded(d, (x1, y1, x2, y2), "#FBFCFD", "#2B5F8A", radius=12, width=2)
        center_text(d, (x1, y1 + 10, x2, y1 + 50), [title], f_box_small, "#244F73")
        center_text(d, (x1, y1 + 52, x2, y1 + 86), [sub], f_note, MUTED)

    # observability
    oy = 1025
    boxes = [
        (225, oy, 654, oy + 70, "Prometheus + Grafana", "连接数 · 延迟 · 积压"),
        (678, oy, 1107, oy + 70, "OpenTelemetry", "端到端链路追踪"),
        (1131, oy, 1560, oy + 70, "ELK / Loki", "网关与服务日志"),
    ]
    for x1, y1, x2, y2, title, sub in boxes:
        rounded(d, (x1, y1, x2, y2), "#FBF9FC", "#8B6F9E", radius=10, width=2)
        center_text(d, (x1, y1 + 6, x2, y1 + 36), [title], font(21, True), "#5B426B")
        center_text(d, (x1, y1 + 38, x2, y1 + 64), [sub], font(17), MUTED)

    # inter-layer arrows
    for y1, y2 in ((255, 283), (420, 448), (685, 713), (840, 868), (995, 1023)):
        arrow(d, (800, y1), (800, y2), TEAL_2, width=4, head=12)

    img.save(OUT / "architecture.png", quality=95)


def sequence():
    W, H = 1600, 1320
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)

    f_title = font(27, True)
    f_lane = font(23, True)
    f_lane_sub = font(15)
    f_msg = font(25)
    f_note = font(20)
    f_legend = font(17)

    d.line((70, 40, 125, 40), fill=TEAL_2, width=3)
    d.text((135, 40), "请求", font=f_legend, fill=MUTED, anchor="lm")
    d.line((195, 40, 250, 40), fill=GREEN, width=3)
    d.text((260, 40), "响应 / 异步", font=f_legend, fill=MUTED, anchor="lm")
    d.text((1540, 40), "消息发送主链路", font=f_title, fill=TEAL, anchor="rm")

    lanes = [
        (140, "发送端 A", "学生 / 教师", "#EAF4F0", TEAL),
        (400, "WSS 网关", "Netty", "#E7F1F4", TEAL),
        (660, "聊天服务", "Spring Boot", "#F7F3E8", "#6B4E16"),
        (920, "Redis", "路由 / Seq / 未读", "#EEF4F1", GREEN),
        (1180, "PostgreSQL / Kafka", "消息 / Outbox", "#F1F3F5", "#2B5F8A"),
        (1440, "接收端 B", "在线设备", "#EAF4F0", TEAL),
    ]
    for x, title, sub, fill, color in lanes:
        rounded(d, (x - 112, 62, x + 112, 138), fill, color, radius=10, width=2)
        d.text((x, 84), title, font=f_lane, fill=color, anchor="ma")
        d.text((x, 111), sub, font=f_lane_sub, fill=MUTED, anchor="ma")
        dashed_vline(d, x, 145, 1150)

    msgs = [
        (140, 400, ["01 发送消息", "client_msg_id"], TEAL_2, False),
        (400, 660, ["02 鉴权 / 限流 / 转发"], TEAL_2, False),
        (660, 920, ["03 校验会话成员"], TEAL_2, False),
        (920, 660, ["04 通过"], GREEN, True),
        (660, 920, ["05 INCR 会话 Seq"], TEAL_2, False),
        (660, 1180, ["06 插入消息"], TEAL_2, False),
        (660, 1180, ["07 写 Outbox 事件"], TEAL_2, False),
        (660, 400, ["08 ack(id, seq)"], GREEN, True),
        (400, 140, ["09 发送成功"], GREEN, True),
        (1180, 660, ["10 Relay 投递 / 消费"], AMBER, True),
        (660, 920, ["11 查询在线路由"], TEAL_2, False),
        (660, 400, ["12 push(message.new)"], TEAL_2, False),
        (400, 1440, ["13 message.new"], TEAL_2, False),
        (1440, 400, ["14 delivery ack"], GREEN, True),
        (400, 660, ["15 回传送达回执"], GREEN, True),
        (660, 1180, ["16 更新回执 / 未读"], TEAL_2, False),
    ]

    y = 205
    step = 58
    for x1, x2, lines, color, dashed in msgs:
        left = x1 + (16 if x2 > x1 else -16)
        right = x2 - (16 if x2 > x1 else -16)
        arrow(d, (left, y), (right, y), color, width=3, dashed=dashed, head=10)
        center = (x1 + x2) / 2
        if lines and lines[0].startswith("06"):
            center = 1035  # keep clear of the 05 label above
        for j, line in enumerate(lines):
            d.text((center, y - 18 - (len(lines) - 1 - j) * 24), line,
                   font=f_msg, fill=color, anchor="ms")
        y += step

    d.rounded_rectangle((65, 1165, 1535, 1285), radius=10, fill=LIGHT, outline=LINE, width=2)
    d.text((85, 1180), "离线分支", font=font(20, True), fill=TEAL)
    d.text((85, 1208),
           "11 查无在线路由 → 收件箱 / 厂商推送；上线后携带 last_seq 增量补拉。",
           font=f_note, fill=MUTED)
    d.text((85, 1240),
           "可靠性：先落库再 ACK；Outbox 重试；client_msg_id 去重；Seq 允许空洞。",
           font=f_note, fill=MUTED)

    img.save(OUT / "message-sequence.png", quality=95)


if __name__ == "__main__":
    architecture()
    sequence()
    print(f"generated: {OUT / 'architecture.png'}")
    print(f"generated: {OUT / 'message-sequence.png'}")
