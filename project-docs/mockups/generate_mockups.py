#!/usr/bin/env python3
"""Generates the launcher mockup SVGs (original design, no show assets)."""
import sys, os

OUT = sys.argv[1]
W, H = 390, 844

C = dict(
    bg="#04070A", panel="#0A1218", panel2="#0E1A22",
    acc="#4CC9F0", accDim="#1E5A6E", amber="#FFB547", red="#FF4D5E", green="#5BE49B",
    tx="#E6F1F5", tx2="#8AA4B0", tx3="#4A606B", line="#1C2C35",
)
COND = "'Barlow Condensed','Arial Narrow',sans-serif"
BODY = "'Barlow','Liberation Sans',sans-serif"
MONO = "'JetBrains Mono','DejaVu Sans Mono',monospace"


def cham(x, y, w, h, c=12, tl=False, tr=True, br=False, bl=True):
    """Rechteck mit 45°-Fasen an den gewählten Ecken."""
    p = []
    p.append(f"M{x + (c if tl else 0)},{y}")
    p.append(f"L{x + w - (c if tr else 0)},{y}")
    if tr: p.append(f"L{x + w},{y + c}")
    p.append(f"L{x + w},{y + h - (c if br else 0)}")
    if br: p.append(f"L{x + w - c},{y + h}")
    p.append(f"L{x + (c if bl else 0)},{y + h}")
    if bl: p.append(f"L{x},{y + h - c}")
    p.append(f"L{x},{y + (c if tl else 0)}")
    if tl: p.append(f"L{x + c},{y}")
    return " ".join(p) + " Z"


def t(x, y, s, size=12, fill=C["tx"], fam=COND, weight=500, anchor="start", ls=0, op=1):
    s = s.replace("&", "&amp;")
    return (f'<text x="{x}" y="{y}" font-family="{fam}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}" letter-spacing="{ls}" opacity="{op}">{s}</text>')


def label(x, y, s, fill=C["tx2"], anchor="start"):
    return t(x, y, s.upper(), 10, fill, COND, 600, anchor, 1.6)


def panel(x, y, w, h, title=None, acc=C["acc"], meta=None, glow=False):
    out = [f'<path d="{cham(x, y, w, h, 10)}" fill="{C["panel"]}" fill-opacity="0.85" stroke="{acc if glow else C["line"]}" stroke-width="1" {"filter=\"url(#glowS)\"" if glow else ""}/>']
    # Kennmarke oben links: kurzer Akzentstrich
    out.append(f'<rect x="{x}" y="{y}" width="3" height="16" fill="{acc}"/>')
    if title:
        out.append(label(x + 12, y + 15, title))
    if meta:
        out.append(label(x + w - 16, y + 15, meta, C["tx3"], "end"))
    return "".join(out)


def hexagon(cx, cy, r, stroke=C["acc"], fill="none", sw=1.2, extra=""):
    import math
    pts = " ".join(f"{cx + r * math.cos(math.radians(a)):.1f},{cy + r * math.sin(math.radians(a)):.1f}" for a in range(30, 390, 60))
    return f'<polygon points="{pts}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>'


# --- einfache Linien-Glyphen (24er Raster, um cx/cy zentriert) ---
def g_phone(cx, cy, col):
    return (f'<path transform="translate({cx - 9},{cy - 9})" d="M3 1 L7 1 L9 6 L6.5 7.5 C7.5 10 8 10.5 10.5 11.5 L12 9 L17 11 L17 15 C17 16.5 15.5 17.5 14 17.3 C7 16.5 1.5 11 0.7 4 C0.5 2.5 1.5 1 3 1 Z" '
            f'fill="none" stroke="{col}" stroke-width="1.4" stroke-linejoin="round"/>')


def g_msg(cx, cy, col):
    return (f'<path transform="translate({cx - 9},{cy - 8})" d="M1 1 H17 V12 H7 L3 16 V12 H1 Z" fill="none" stroke="{col}" stroke-width="1.4" stroke-linejoin="round"/>')


def g_cam(cx, cy, col):
    return (f'<g transform="translate({cx - 9},{cy - 8})" fill="none" stroke="{col}" stroke-width="1.4"><path d="M1 4 H5 L7 1 H11 L13 4 H17 V15 H1 Z" stroke-linejoin="round"/><circle cx="9" cy="9.5" r="3.2"/></g>')


def g_web(cx, cy, col):
    return (f'<g fill="none" stroke="{col}" stroke-width="1.3"><circle cx="{cx}" cy="{cy}" r="8.5"/><ellipse cx="{cx}" cy="{cy}" rx="3.6" ry="8.5"/><line x1="{cx - 8.5}" y1="{cy}" x2="{cx + 8.5}" y2="{cy}"/></g>')


def g_mic(cx, cy, col):
    return (f'<g fill="none" stroke="{col}" stroke-width="1.4"><rect x="{cx - 3.5}" y="{cy - 9}" width="7" height="11" rx="3.5"/><path d="M{cx - 7} {cy - 1} a7 7 0 0 0 14 0"/><line x1="{cx}" y1="{cy + 6}" x2="{cx}" y2="{cy + 9}"/></g>')


def g_play(cx, cy, col, s=1.0, filled=True):
    return f'<polygon points="{cx - 6 * s},{cy - 8 * s} {cx + 8 * s},{cy} {cx - 6 * s},{cy + 8 * s}" fill="{col if filled else "none"}" stroke="{col}" stroke-width="1.3" stroke-linejoin="round"/>'


def g_pause(cx, cy, col, s=1.0):
    return (f'<rect x="{cx - 6 * s}" y="{cy - 8 * s}" width="{4 * s}" height="{16 * s}" fill="{col}"/>'
            f'<rect x="{cx + 2 * s}" y="{cy - 8 * s}" width="{4 * s}" height="{16 * s}" fill="{col}"/>')


def g_skip(cx, cy, col, back=False, s=1.0):
    d = -1 if back else 1
    return (f'<polygon points="{cx - 6 * s * d},{cy - 7 * s} {cx + 4 * s * d},{cy} {cx - 6 * s * d},{cy + 7 * s}" fill="{col}"/>'
            f'<rect x="{cx + (4 * s if d == 1 else -5.5 * s)}" y="{cy - 7 * s}" width="{1.6 * s}" height="{14 * s}" fill="{col}"/>')


def tile(cx, cy, letters, col=C["acc"], badge=None, name=None, r=22):
    out = [hexagon(cx, cy, r, col, C["panel2"], 1.2)]
    out.append(t(cx, cy + 6, letters, 17, col, COND, 600, "middle"))
    if badge:
        out.append(f'<rect x="{cx + r - 10}" y="{cy - r - 2}" width="18" height="13" fill="{C["amber"]}"/>')
        out.append(t(cx + r - 1, cy - r + 8.5, str(badge), 10, C["bg"], MONO, 700, "middle"))
    if name:
        out.append(label(cx, cy + r + 15, name, C["tx2"], "middle"))
    return "".join(out)


DEFS = f"""<defs>
<filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<filter id="glowS" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<linearGradient id="bgG" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#071018"/><stop offset="1" stop-color="{C['bg']}"/></linearGradient>
<pattern id="scan" width="4" height="4" patternUnits="userSpaceOnUse"><rect width="4" height="1" fill="#ffffff" opacity="0.025"/></pattern>
</defs>"""


def frame(acc=C["acc"], title="DECK", meta=""):
    """Grundgerüst: Hintergrund, Statusleiste, Hauptrahmen mit Lasche, Seitenschiene."""
    o = [f'<rect width="{W}" height="{H}" fill="url(#bgG)"/>', f'<rect width="{W}" height="{H}" fill="url(#scan)"/>']
    # System-Statusleiste (gehört Android, nur angedeutet)
    o.append(t(24, 22, "21:44", 12, C["tx3"], MONO, 500))
    o.append(t(W - 24, 22, "5G ▮▮▮ 82%", 11, C["tx3"], MONO, 500, "end"))
    # Hauptrahmen
    fx, fy, fw, fh = 14, 40, 320, 772
    o.append(f'<path d="{cham(fx, fy, fw, fh, 22, tl=False, tr=True, br=False, bl=True)}" fill="{C["bg"]}" fill-opacity="0.6" stroke="{acc}" stroke-width="1.6" filter="url(#glow)"/>')
    # Lasche oben (Titel-Tab)
    o.append(f'<path d="{cham(fx + 16, fy - 10, 150, 22, 8, tl=True, tr=True, br=False, bl=False)}" fill="{C["panel2"]}" stroke="{acc}" stroke-width="1"/>')
    o.append(t(fx + 30, fy + 6, title, 13, C["tx"], COND, 700, ls=2.4))
    if meta:
        o.append(label(fx + fw - 30, fy + 22, meta, C["tx3"], "end"))
    # Kerbe rechts (Übergang zur Seitenschiene)
    o.append(f'<path d="M{fx + fw},{fy + 300} l8,8 v120 l-8,8" fill="none" stroke="{acc}" stroke-width="1.2" opacity="0.8"/>')
    return o


def rail(items, acc=C["acc"], y0=150):
    """Seitenschiene außerhalb des Rahmens: Status + Schnellzugriffe."""
    o = []
    x = 362
    for i, (lab, on) in enumerate(items):
        cy = y0 + i * 62
        col = acc if on else C["tx3"]
        o.append(hexagon(x, cy, 14, col, C["panel2"] if on else "none", 1.2))
        if on:
            o.append(f'<circle cx="{x}" cy="{cy}" r="3" fill="{acc}" filter="url(#glowS)"/>')
        o.append(label(x, cy + 28, lab, col, "middle"))
    return o


def grip(acc=C["acc"], y=690):
    """Daumenzone: Kerbenlinie, darunter die Haupt-Bedienelemente."""
    return [f'<path d="M30,{y} H120 l6,-6 H222 l6,6 H318" fill="none" stroke="{acc}" stroke-width="1" opacity="0.6"/>',
            f'<rect x="166" y="{y - 4}" width="16" height="2" fill="{acc}"/>']


def cmdline(y=770, text="SEARCH OR COMMAND", acc=C["acc"], active=False, typed=None):
    o = [f'<path d="{cham(30, y - 22, 288, 36, 8, tl=True, tr=False, br=True, bl=False)}" fill="{C["panel2"]}" stroke="{acc if active else C["line"]}" stroke-width="1" {"filter=\"url(#glowS)\"" if active else ""}/>']
    o.append(t(44, y + 1, "›", 18, acc, MONO, 700))
    if typed:
        o.append(t(62, y + 1, typed, 15, C["tx"], MONO, 500))
        o.append(f'<rect x="{64 + len(typed) * 9.1}" y="{y - 12}" width="8" height="16" fill="{acc}" opacity="0.9"/>')
    else:
        o.append(t(62, y, text, 13, C["tx3"], COND, 600, ls=1.6))
    o.append(g_mic(300, y - 3, acc))
    return o


def svg(parts):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">{DEFS}{"".join(parts)}</svg>'


def bar(x, y, w, frac, col=C["acc"], h=3):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{C["line"]}"/>'
            f'<rect x="{x}" y="{y}" width="{w * frac:.0f}" height="{h}" fill="{col}" filter="url(#glowS)"/>')


# ---------------- 01 Deck (Startseite) ----------------
def home():
    o = frame(title="DECK", meta="3 SIGNALS")
    o += rail([("WIFI", True), ("BT", True), ("DND", False), ("LIGHT", False), ("ROTATE", False)])
    o.append(t(34, 146, "21:44", 92, C["tx"], COND, 500, ls=-1))
    o.append(t(36, 172, "SUN · 08 NOV", 13, C["tx2"], MONO, 500, ls=1))
    o.append(t(318, 172, "ALARM 06:30", 13, C["amber"], MONO, 500, "end"))
    # Jetzt läuft
    o.append(panel(30, 196, 288, 104, "Now playing", meta="MUSIC"))
    o.append(t(44, 240, "Low Orbit Lullaby", 20, C["tx"], BODY, 600))
    o.append(t(44, 258, "The Kepler Set", 13, C["tx2"], BODY, 400))
    o.append(bar(44, 274, 160, 0.42))
    o.append(t(44, 292, "1:42 / 4:03", 10, C["tx3"], MONO))
    o.append(g_skip(232, 262, C["tx2"], back=True))
    o.append(hexagon(266, 262, 17, C["acc"], C["panel2"]))
    o.append(g_pause(266, 262, C["acc"], 0.7))
    o.append(g_skip(300, 262, C["tx2"]))
    # Zwei halbe Panels
    o.append(panel(30, 314, 140, 104, "Next", acc=C["amber"]))
    o.append(t(44, 356, "Dentist", 18, C["tx"], BODY, 600))
    o.append(t(44, 376, "Mon 09:15", 13, C["tx2"], MONO))
    o.append(t(44, 404, "IN 11 H", 11, C["amber"], COND, 600, ls=1.4))
    o.append(panel(178, 314, 140, 104, "Today · call", acc=C["green"]))
    o.append(hexagon(204, 362, 15, C["green"], C["panel2"]))
    o.append(t(204, 367, "AL", 13, C["green"], COND, 700, "middle"))
    o.append(t(228, 360, "Alex", 18, C["tx"], BODY, 600))
    o.append(t(228, 378, "12 days", 12, C["tx2"], MONO))
    o.append(t(192, 404, "TAP TO CALL", 11, C["green"], COND, 600, ls=1.4))
    # Signale
    o.append(panel(30, 432, 288, 112, "Signals", meta="3 NEW"))
    rows = [("Me", "Messages", "Sam: See you at 8?", "2"), ("Ma", "Mail", "Invoice November", "1")]
    for i, (lt, app, txt, n) in enumerate(rows):
        yy = 470 + i * 38
        o.append(f'<path d="{cham(44, yy - 14, 28, 28, 6)}" fill="{C["panel2"]}" stroke="{C["accDim"]}"/>')
        o.append(t(58, yy + 5, lt, 13, C["acc"], COND, 700, "middle"))
        o.append(t(82, yy - 1, app.upper(), 10, C["tx3"], COND, 600, ls=1.4))
        o.append(t(82, yy + 13, txt, 13, C["tx"], BODY, 500))
        o.append(t(304, yy + 6, n, 13, C["amber"], MONO, 700, "end"))
    # Mai-App-Panel (Fitness)
    o.append(panel(30, 558, 288, 96, "Training", meta="FROM FITNESS"))
    o.append(t(44, 600, "Push day", 18, C["tx"], BODY, 600))
    o.append(t(44, 620, "Streak 4 · last: Thu", 12, C["tx2"], MONO))
    for i, hgt in enumerate([10, 22, 16, 30, 0, 26, 34]):
        x = 210 + i * 14
        o.append(f'<rect x="{x}" y="{636 - hgt}" width="8" height="{max(hgt, 2)}" fill="{C["acc"] if i == 6 else C["accDim"]}"/>')
    o.append(label(210, 646, "M T W T F S S", C["tx3"]))
    o += grip()
    tiles = [(g_phone, "Phone"), (g_msg, "Messages"), (g_cam, "Camera"), (g_web, "Browser")]
    for i, (g, n) in enumerate(tiles):
        cx = 66 + i * 72
        o.append(hexagon(cx, 718, 18, C["acc"], C["panel2"]))
        o.append(g(cx, 718, C["acc"]))
    o += cmdline(782)
    return svg(o)


# ---------------- 02 Index (App-Liste) ----------------
def index():
    o = frame(title="INDEX", meta="64 APPS")
    o.append(label(34, 78, "Likely now · 07–09 h"))
    sugg = [("Ca", "Calendar"), ("Mu", "Music"), ("Fi", "Fitness"), ("Ma", "Maps")]
    for i, (lt, n) in enumerate(sugg):
        o.append(tile(64 + i * 72, 116, lt, C["acc"], name=n))
    o.append(f'<line x1="30" y1="170" x2="318" y2="170" stroke="{C["line"]}"/>')
    apps = [("C", ["Calculator", "Calendar", "Camera", "Clock", "Contacts"]),
            ("F", ["Files", "Fitness"]), ("G", ["Gallery"]), ("M", ["Maps", "Messages", "Music"]),
            ("N", ["Notes"])]
    y = 196
    for letter, names in apps:
        o.append(t(34, y + 4, letter, 22, C["acc"], COND, 600))
        o.append(f'<line x1="58" y1="{y - 3}" x2="318" y2="{y - 3}" stroke="{C["line"]}"/>')
        for n in names:
            hl = n == "Calendar"
            if hl:
                o.append(f'<path d="{cham(58, y - 14, 260, 30, 6)}" fill="{C["panel2"]}" stroke="{C["acc"]}" filter="url(#glowS)"/>')
            o.append(f'<rect x="68" y="{y - 7}" width="16" height="16" fill="none" stroke="{C["acc"] if hl else C["tx3"]}" stroke-width="1.2"/>')
            o.append(t(76, y + 5, n[0], 11, C["acc"] if hl else C["tx2"], COND, 700, "middle"))
            o.append(t(96, y + 6, n.upper(), 16, C["tx"] if hl else C["tx"], COND, 600 if hl else 500, ls=1.2))
            if n == "Messages":
                o.append(t(306, y + 6, "2", 13, C["amber"], MONO, 700, "end"))
            if hl:
                o.append(label(306, y + 5, "Hold = actions", C["tx3"], "end"))
            y += 32
        y += 8
    # Buchstaben-Leiste in der Seitenschiene
    letters = "ABCDEFGHIJKLMNOPRSTUVWZ#"
    for i, L in enumerate(letters):
        yy = 96 + i * 25
        on = L == "C"
        if on:
            o.append(hexagon(362, yy - 4, 11, C["acc"], C["panel2"]))
        o.append(t(362, yy + 1, L, 12, C["acc"] if on else C["tx3"], COND, 700, "middle"))
    o += grip(y=726)
    o += cmdline(782, "TYPE TO FILTER")
    return svg(o)


# ---------------- 03 Comms (Telefon) ----------------
def comms():
    acc = C["green"]
    o = frame(acc=acc, title="COMMS", meta="FAVOURITES")
    o += rail([("KEYPAD", True), ("RECENT", False), ("VOICE", False)], acc=acc, y0=160)
    favs = [("AL", "Alex"), ("SA", "Sam"), ("MI", "Mia"), ("JO", "Jonas"), ("KI", "Kim"), ("MU", "Mum")]
    for i, (lt, n) in enumerate(favs):
        cx = 72 + (i % 3) * 102
        cy = 116 + (i // 3) * 98
        o.append(hexagon(cx, cy, 30, acc, C["panel2"], 1.3))
        o.append(t(cx, cy + 8, lt, 22, acc, COND, 600, "middle"))
        o.append(t(cx, cy + 50, n, 14, C["tx"], BODY, 500, "middle"))
    o.append(panel(30, 302, 288, 230, "Recent", acc=acc, meta="TODAY"))
    rec = [("Alex", "OUT", "12 min", "18:02", C["tx2"]), ("Sam", "MISSED", "", "14:40", C["red"]),
           ("+49 151 ···· 21", "IN", "2 min", "11:15", C["tx2"]), ("Mum", "OUT", "34 min", "Sat", C["tx2"])]
    for i, (n, kind, dur, when, col) in enumerate(rec):
        yy = 346 + i * 46
        o.append(f'<line x1="44" y1="{yy + 18}" x2="304" y2="{yy + 18}" stroke="{C["line"]}"/>')
        o.append(t(44, yy, n, 16, C["red"] if kind == "MISSED" else C["tx"], BODY, 600))
        o.append(t(44, yy + 13, f"{kind}  {dur}".strip(), 10, col, COND, 600, ls=1.4))
        o.append(t(304, yy + 4, when, 12, C["tx2"], MONO, 500, "end"))
    o.append(panel(30, 546, 288, 120, "Contact", acc=acc))
    o.append(t(44, 590, "Sam", 22, C["tx"], BODY, 600))
    o.append(t(44, 610, "Mobile · last talk 3 weeks", 12, C["tx2"], MONO))
    for i, (lab, col) in enumerate([("CALL", acc), ("TEXT", C["acc"]), ("VIDEO", C["tx2"])]):
        x = 44 + i * 92
        o.append(f'<path d="{cham(x, 626, 84, 28, 7)}" fill="{C["panel2"]}" stroke="{col}"/>')
        o.append(t(x + 42, 645, lab, 12, col, COND, 700, "middle", 2))
    o += grip(acc)
    o.append(f'<path d="{cham(110, 702, 128, 44, 10)}" fill="{acc}" fill-opacity="0.12" stroke="{acc}" stroke-width="1.6" filter="url(#glowS)"/>')
    o.append(g_phone(150, 724, acc))
    o.append(t(186, 730, "DIAL", 16, acc, COND, 700, "middle", 3))
    o += cmdline(782, "NAME OR NUMBER", acc=acc)
    return svg(o)


# ---------------- 04 Media ----------------
def media():
    acc = C["amber"]
    o = frame(acc=acc, title="MEDIA", meta="SOURCE · MUSIC")
    o += rail([("OUTPUT", True), ("QUEUE", False), ("SLEEP", False)], acc=acc, y0=160)
    cx, cy = 174, 220
    for r, op in [(120, 0.25), (96, 0.4), (72, 0.6)]:
        o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{acc}" stroke-width="1" opacity="{op}"/>')
    # Fortschritt als Bogen
    import math
    a = math.radians(-90 + 360 * 0.42)
    ex, ey = cx + 120 * math.cos(a), cy + 120 * math.sin(a)
    o.append(f'<path d="M{cx},{cy - 120} A120,120 0 0 1 {ex:.1f},{ey:.1f}" fill="none" stroke="{acc}" stroke-width="3" filter="url(#glow)"/>')
    o.append(f'<circle cx="{ex:.1f}" cy="{ey:.1f}" r="5" fill="{acc}" filter="url(#glowS)"/>')
    # Cover: zweifarbig (Duotone) im gefasten Rahmen
    o.append(f'<path d="{cham(cx - 52, cy - 52, 104, 104, 12)}" fill="#1a1408" stroke="{acc}" stroke-width="1"/>')
    for i in range(9):
        o.append(f'<line x1="{cx - 40}" y1="{cy - 36 + i * 9}" x2="{cx + 40 - (i % 3) * 14}" y2="{cy - 36 + i * 9}" stroke="{acc}" stroke-width="2" opacity="{0.15 + (i % 4) * 0.12}"/>')
    o.append(label(cx, cy + 74, "COVER · DUOTONE", C["tx3"], "middle"))
    o.append(t(34, 386, "Low Orbit Lullaby", 26, C["tx"], BODY, 600))
    o.append(t(34, 410, "The Kepler Set · Night Shift (2025)", 13, C["tx2"], BODY))
    o.append(bar(34, 430, 284, 0.42, acc))
    o.append(t(34, 448, "1:42", 11, C["tx2"], MONO))
    o.append(t(318, 448, "-2:21", 11, C["tx2"], MONO, 500, "end"))
    o.append(panel(30, 470, 288, 196, "Up next", acc=acc, meta="4 IN QUEUE"))
    q = [("Docking Window", "4:41"), ("Belt Static", "3:58"), ("Thrust Gravity", "5:12"), ("Quiet Hull", "3:20")]
    for i, (n, d) in enumerate(q):
        yy = 514 + i * 38
        o.append(t(44, yy, f"0{i + 1}", 12, C["tx3"], MONO))
        o.append(t(70, yy, n, 15, C["tx"], BODY, 500))
        o.append(t(304, yy, d, 12, C["tx2"], MONO, 500, "end"))
    o += grip(acc)
    o.append(g_skip(96, 728, C["tx2"], back=True, s=1.3))
    o.append(hexagon(174, 728, 30, acc, C["panel2"], 1.6, 'filter="url(#glowS)"'))
    o.append(g_pause(174, 728, acc, 1.0))
    o.append(g_skip(252, 728, C["tx2"], s=1.3))
    o += cmdline(782, "SEARCH MUSIC", acc=acc)
    return svg(o)


# ---------------- 05 Command (Assistent) ----------------
def command():
    o = frame(title="COMMAND", meta="ON-DEVICE")
    o.append(f'<rect x="14" y="40" width="320" height="772" fill="{C["bg"]}" opacity="0.4"/>')
    o.append(label(34, 84, "Try"))
    chips = ["timer 10", "= 12*7", "wifi", "call mum"]
    x = 34
    for c in chips:
        w = 16 + len(c) * 7.4
        o.append(f'<path d="{cham(x, 94, w, 24, 6)}" fill="none" stroke="{C["line"]}"/>')
        o.append(t(x + 8, 110, c, 12, C["tx2"], MONO))
        x += w + 8
    # Ergebnisse von unten nach oben (Daumen)
    o.append(label(34, 360, "Results for “alex”"))
    res = [
        ("CALL ALEX", "Mobile · +49 151 ···· 08", C["green"], g_phone, True),
        ("MESSAGE ALEX", "Messages", C["acc"], g_msg, False),
        ("OPEN CONTACT", "Contacts", C["tx2"], None, False),
        ("ALEX · 12 DAYS AGO", "From Ruf durch · plan a call", C["tx2"], None, False),
    ]
    for i, (h, s, col, g, top) in enumerate(res):
        yy = 384 + i * 66
        o.append(f'<path d="{cham(30, yy, 288, 56, 10)}" fill="{C["panel"]}" stroke="{col if top else C["line"]}" {"filter=\"url(#glowS)\"" if top else ""}/>')
        if g:
            o.append(g(56, yy + 28, col))
        else:
            o.append(hexagon(56, yy + 28, 12, col))
        o.append(t(82, yy + 26, h, 16, C["tx"], COND, 700, ls=1.6))
        o.append(t(82, yy + 44, s, 12, C["tx2"], BODY))
        if top:
            o.append(label(304, yy + 26, "ENTER ↵", col, "end"))
    o.append(panel(30, 652, 288, 60, None, acc=C["tx3"]))
    o.append(t(44, 678, "= 12*7", 14, C["tx2"], MONO))
    o.append(t(304, 680, "84", 22, C["acc"], MONO, 600, "end"))
    o.append(t(44, 698, "Calculator inline · nothing leaves the phone", 11, C["tx3"], BODY))
    o += cmdline(782, acc=C["acc"], active=True, typed="alex")
    return svg(o)


# ---------------- 00 Design-Sprache (Bogen) ----------------
def language():
    WW, HH = 1200, 760
    o = [f'<rect width="{WW}" height="{HH}" fill="{C["bg"]}"/>', f'<rect width="{WW}" height="{HH}" fill="url(#scan)"/>']
    o.append(t(40, 60, "DESIGN LANGUAGE · WORKING SHEET", 26, C["tx"], COND, 700, ls=3))
    o.append(t(40, 84, "Original system inspired by hand-terminal sci-fi: dark glass, one glowing frame, chamfered geometry, honest data.", 14, C["tx2"], BODY))
    # Farben
    o.append(label(40, 130, "Colour tokens"))
    sw = [("bg", C["bg"], "Void · #04070A"), ("panel", C["panel"], "Panel · #0A1218"), ("line", C["line"], "Line · #1C2C35"),
          ("tx", C["tx"], "Text · #E6F1F5"), ("tx2", C["tx2"], "Text 2 · #8AA4B0"),
          ("acc", C["acc"], "Ion (default) · #4CC9F0"), ("amber", C["amber"], "Amber · media/time"),
          ("green", C["green"], "Signal green · comms"), ("red", C["red"], "Alert · only urgent")]
    for i, (k, col, n) in enumerate(sw):
        x = 40 + (i % 5) * 112
        y = 144 + (i // 5) * 92
        o.append(f'<path d="{cham(x, y, 100, 52, 8)}" fill="{col}" stroke="{C["line"]}"/>')
        o.append(t(x, y + 68, n, 11, C["tx2"], BODY))
    # Schrift
    o.append(label(640, 130, "Type"))
    o.append(t(640, 186, "21:44", 64, C["tx"], COND, 500))
    o.append(t(800, 160, "DISPLAY · Barlow Condensed", 12, C["tx2"], MONO))
    o.append(t(800, 180, "clock, headers, labels", 12, C["tx3"], MONO))
    o.append(t(640, 220, "LABEL · CONDENSED CAPS +1.6", 12, C["tx2"], COND, 600, ls=1.6))
    o.append(t(640, 248, "Body text stays sentence case and readable.", 17, C["tx"], BODY, 500))
    o.append(t(640, 274, "1:42 / 4:03 · 82% · 06:30  — numbers in mono", 14, C["acc"], MONO))
    # Formen
    o.append(label(40, 360, "Shapes"))
    o.append(f'<path d="{cham(40, 380, 150, 90, 14)}" fill="{C["panel"]}" stroke="{C["acc"]}" filter="url(#glowS)"/>')
    o.append(t(115, 490, "Chamfer 45° (2 corners)", 11, C["tx2"], BODY, 400, "middle"))
    o.append(f'<path d="{cham(220, 380, 150, 90, 10)}" fill="{C["panel"]}" stroke="{C["line"]}"/><rect x="220" y="380" width="3" height="16" fill="{C["acc"]}"/>')
    o.append(label(232, 395, "Panel"))
    o.append(t(295, 490, "Panel + accent tick", 11, C["tx2"], BODY, 400, "middle"))
    o.append(hexagon(440, 425, 30, C["acc"], C["panel2"], 1.3))
    o.append(t(440, 433, "AL", 20, C["acc"], COND, 600, "middle"))
    o.append(t(440, 490, "Hex = tappable token", 11, C["tx2"], BODY, 400, "middle"))
    o.append(f'<path d="{cham(510, 405, 100, 22, 8, tl=True, tr=True, br=False, bl=False)}" fill="{C["panel2"]}" stroke="{C["acc"]}"/>')
    o.append(t(524, 421, "TITLE TAB", 12, C["tx"], COND, 700, ls=2))
    o.append(t(560, 490, "Tab = where am I", 11, C["tx2"], BODY, 400, "middle"))
    # Leuchten
    o.append(label(640, 360, "Glow budget"))
    for i, (n, filt, sw_) in enumerate([("0 · resting", "", 1), ("1 · focus", 'filter="url(#glowS)"', 1), ("2 · active frame", 'filter="url(#glow)"', 1.6)]):
        x = 640 + i * 180
        o.append(f'<path d="{cham(x, 380, 150, 90, 14)}" fill="{C["panel"]}" stroke="{C["acc"] if i else C["line"]}" stroke-width="{sw_}" {filt}/>')
        o.append(t(x + 75, 490, n, 11, C["tx2"], BODY, 400, "middle"))
    # Kontextfarben
    o.append(label(40, 560, "Context colour = where you are (one accent per screen)"))
    for i, (n, col) in enumerate([("DECK · INDEX · COMMAND", C["acc"]), ("COMMS", C["green"]), ("MEDIA", C["amber"]), ("ALERT ONLY", C["red"])]):
        x = 40 + i * 280
        o.append(f'<path d="{cham(x, 576, 250, 60, 12)}" fill="{C["bg"]}" stroke="{col}" stroke-width="1.6" filter="url(#glow)"/>')
        o.append(t(x + 20, 612, n, 15, col, COND, 700, ls=2))
    o.append(t(40, 690, "Rules: glow is decoration, never the only carrier of meaning · text contrast ≥ 4.5:1 without glow · one glowing frame per screen · no fake data, every label is real", 13, C["tx2"], BODY))
    o.append(t(40, 714, "High-contrast mode: glow off, strokes 2 px, panels opaque · Reduce motion: no scan/draw-in, instant fades", 13, C["tx2"], BODY))
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{WW}" height="{HH}" viewBox="0 0 {WW} {HH}">{DEFS}{"".join(o)}</svg>'


for name, fn in [("00_design-language", language), ("01_deck", home), ("02_index", index), ("03_comms", comms),
                 ("04_media", media), ("05_command", command)]:
    with open(os.path.join(OUT, name + ".svg"), "w") as f:
        f.write(fn())
print("ok")
