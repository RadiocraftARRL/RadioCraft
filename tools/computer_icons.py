"""Placeholder icons for the radio computer's Program Manager (1.12, launcher and SETUP).

Writes one 32 x 32 PNG per program to src/main/resources/assets/radiocraft/textures/gui/computer/icons/<id>.png,
drawn only in the sixteen EGA colours (the game recolours them per SETUP colour scheme by matching those exact
colours, so do not anti-alias or add other colours). Simple Windows 3.1 style pictograms: a black outline, flat
fills, transparent background. Listed on doc/ModelDesignBounty.md for an artist to redo (keep the palette and size).

Run from the repository root:  python tools/computer_icons.py
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join('src', 'main', 'resources', 'assets', 'radiocraft', 'textures', 'gui', 'computer', 'icons')

# The EGA palette, as DosColours has it.
K, B, G, C, R, M, N, L = 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA
D, LB, LG, LC, LR, LM, Y, W = 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF


def rgb(c):
    return (c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, 255)


class Icon:
    def __init__(self):
        self.img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def rect(self, x0, y0, x1, y1, fill=None, outline=K):
        self.d.rectangle([x0, y0, x1, y1], fill=rgb(fill) if fill is not None else None,
                         outline=rgb(outline) if outline is not None else None)

    def line(self, pts, c=K, w=1):
        self.d.line(pts, fill=rgb(c), width=w)

    def px(self, x, y, c):
        self.d.point((x, y), fill=rgb(c))

    def ellipse(self, x0, y0, x1, y1, fill=None, outline=K):
        self.d.ellipse([x0, y0, x1, y1], fill=rgb(fill) if fill is not None else None,
                       outline=rgb(outline) if outline is not None else None)

    def poly(self, pts, fill=None, outline=K):
        self.d.polygon(pts, fill=rgb(fill) if fill is not None else None,
                       outline=rgb(outline) if outline is not None else None)

    def save(self, name):
        self.img.save(os.path.join(OUT, name + '.png'))


# -------------------- SHARED PIECES --------------------

def page(i, x0=6, y0=3, x1=25, y1=28, fill=W):
    """A sheet of paper with a folded corner."""
    i.poly([(x0, y0), (x1 - 5, y0), (x1, y0 + 5), (x1, y1), (x0, y1)], fill=fill)
    i.poly([(x1 - 5, y0), (x1 - 5, y0 + 5), (x1, y0 + 5)], fill=L)


FONT = {'A': ['010', '101', '111', '101', '101'], 'R': ['110', '101', '110', '101', '101'],
        'Y': ['101', '101', '010', '010', '010']}


def glyph(i, x, y, text, c):
    """Tiny 3 x 5 capitals, pixel by pixel (no font smoothing: every pixel stays in the palette)."""
    for n, ch in enumerate(text):
        for r, row in enumerate(FONT[ch]):
            for k, bit in enumerate(row):
                if bit == '1':
                    i.px(x + n * 4 + k, y + r, c)


def text_lines(i, x0, y0, x1, rows, c=D, step=3):
    for r in range(rows):
        i.line([(x0, y0 + r * step), (x1 - (r * 3) % 6, y0 + r * step)], c)


def monitor(i, screen=B, x0=3, y0=3, x1=28, y1=22):
    """A PC monitor with a stand."""
    i.rect(x0, y0, x1, y1, fill=L)
    i.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 3, fill=screen)
    i.rect(12, y1, 19, y1 + 3, fill=D)
    i.rect(8, y1 + 4, 23, y1 + 6, fill=L)


def radio(i, x0=3, y0=12, x1=28, y1=27, face=L):
    """A transceiver: a box with a dial and a knob."""
    i.rect(x0, y0, x1, y1, fill=face)
    i.rect(x0 + 2, y0 + 2, x0 + 13, y0 + 7, fill=K)
    i.ellipse(x1 - 9, y0 + 3, x1 - 3, y0 + 9, fill=D)
    for k in range(3):
        i.rect(x0 + 2 + k * 4, y1 - 4, x0 + 4 + k * 4, y1 - 2, fill=D)


def antenna(i, x, y0, y1, c=K):
    i.line([(x, y0), (x, y1)], c)
    i.line([(x - 3, y0 + 2), (x + 3, y0 + 2)], c)


def waves(i, x, y, c=LR, n=3):
    for k in range(n):
        r = 3 + k * 3
        i.d.arc([x - r, y - r, x + r, y + r], 300, 60, fill=rgb(c))


def floppy(i, x0, y0, s=14, body=B):
    """A 3.5 inch disk: a body with one clipped corner, a metal shutter with its window, a label inset below."""
    x1, y1 = x0 + s, y0 + s
    i.poly([(x0, y0), (x1 - 2, y0), (x1, y0 + 2), (x1, y1), (x0, y1)], fill=body)
    i.rect(x0 + 3, y0 + 1, x1 - 4, y0 + 5, fill=L, outline=None)
    i.rect(x1 - 7, y0 + 2, x1 - 6, y0 + 4, fill=D, outline=None)
    i.rect(x0 + 3, y1 - 6, x1 - 3, y1 - 2, fill=W, outline=None)
    i.line([(x0 + 4, y1 - 5), (x1 - 4, y1 - 5)], D)


# -------------------- THE ICONS --------------------

def edit():
    i = Icon(); page(i); text_lines(i, 9, 10, 22, 6)
    i.line([(14, 26), (27, 13)], Y, 3); i.line([(13, 27), (15, 25)], K); i.save('edit')


def sysinfo():
    i = Icon(); monitor(i, B)
    i.rect(13, 7, 15, 8, fill=W, outline=None); i.rect(13, 10, 15, 16, fill=W, outline=None)
    i.rect(17, 14, 24, 16, fill=LC, outline=None); i.save('sysinfo')


def bandplan():
    i = Icon(); i.rect(2, 5, 29, 26, fill=W)
    cols = [LR, Y, LG, LC, LB, LM]
    for r in range(4):
        x = 4
        for k in range(3):
            w = 4 + (r * 3 + k * 5) % 6
            i.rect(x, 8 + r * 4, min(27, x + w), 10 + r * 4, fill=cols[(r + k) % 6], outline=None)
            x += w + 1
    i.line([(4, 24), (27, 24)], K); i.save('bandplan')


def install():
    i = Icon(); floppy(i, 8, 1, 15)
    i.poly([(13, 19), (19, 19), (19, 24), (23, 24), (16, 30), (9, 24), (13, 24)], fill=LG); i.save('install')


def setup():
    i = Icon()
    i.ellipse(6, 6, 25, 25, fill=L)
    for (x, y) in [(14, 2), (14, 25), (2, 14), (25, 14), (5, 5), (22, 22), (5, 22), (22, 5)]:
        i.rect(x, y, x + 4, y + 4, fill=L)
    i.ellipse(11, 11, 20, 20, fill=B)
    i.rect(23, 23, 30, 30, fill=Y); i.rect(25, 25, 28, 28, fill=LR, outline=None); i.save('setup')


def about():
    i = Icon(); i.ellipse(3, 3, 28, 28, fill=LB)
    i.rect(14, 7, 17, 10, fill=W); i.rect(14, 13, 17, 24, fill=W); i.rect(12, 13, 14, 14, fill=W, outline=None)
    i.rect(12, 23, 19, 24, fill=W); i.save('about')


def license_study():
    i = Icon(); i.poly([(3, 8), (15, 5), (15, 27), (3, 29)], fill=W); i.poly([(16, 5), (28, 8), (28, 29), (16, 27)], fill=W)
    text_lines(i, 5, 11, 13, 5, D, 3); text_lines(i, 18, 11, 26, 5, D, 3)
    i.ellipse(20, 1, 30, 11, fill=Y); glyph(i, 24, 4, 'A', R); i.save('license')


def bookedit():
    i = Icon(); i.rect(5, 3, 24, 28, fill=N); i.rect(7, 3, 9, 28, fill=R, outline=None)
    i.rect(11, 7, 21, 12, fill=Y); i.line([(18, 28), (29, 14)], W, 3); i.line([(17, 29), (19, 27)], K); i.save('bookedit')


def logbook():
    i = Icon(); i.rect(4, 3, 27, 28, fill=G); i.rect(7, 6, 24, 25, fill=W)
    for r in range(5):
        i.line([(8, 9 + r * 3), (23, 9 + r * 3)], LB)
    i.line([(12, 7), (12, 24)], LR); i.save('logbook')


def freqman():
    i = Icon(); i.rect(3, 4, 28, 27, fill=W)
    for r in range(6):
        i.rect(5, 7 + r * 3, 9, 8 + r * 3, fill=LB, outline=None); i.line([(11, 7 + r * 3), (25, 7 + r * 3)], D)
    i.rect(4, 13, 27, 15, fill=None, outline=R); i.save('freqman')


def programmer():
    i = Icon(); radio(i, 10, 4, 22, 28, face=D); i.rect(12, 6, 20, 10, fill=LG)
    i.line([(2, 20), (10, 20)], K, 2); i.rect(0, 17, 3, 23, fill=Y); i.save('programmer')


def solar():
    i = Icon(); i.ellipse(8, 8, 23, 23, fill=Y)
    for (a, b, c2, d2) in [(15, 1, 16, 5), (15, 26, 16, 30), (1, 15, 5, 16), (26, 15, 30, 16), (5, 5, 8, 8), (23, 23, 26, 26), (5, 26, 8, 23), (23, 8, 26, 5)]:
        i.line([(a, b), (c2, d2)], N, 2)
    i.ellipse(12, 12, 15, 15, fill=N, outline=None); i.save('solar')


def aprs():
    """A map pin on a green map."""
    i = Icon(); i.rect(2, 4, 29, 27, fill=LG)
    i.poly([(10, 13), (22, 13), (16, 25)], fill=LR, outline=None)
    i.ellipse(9, 6, 23, 19, fill=LR, outline=None)
    i.line([(10, 15), (16, 25), (22, 15)], K)
    i.d.arc([9, 6, 23, 19], 150, 30, fill=rgb(K))
    i.ellipse(14, 10, 18, 14, fill=W, outline=None); i.save('aprs')


def wefax():
    i = Icon(); i.rect(3, 5, 28, 27, fill=W)
    for k in range(4):
        i.d.arc([4 + k * 2, 8 + k * 2, 27 - k * 2, 31 - k * 2], 200, 340, fill=rgb(LB))
    i.poly([(10, 14), (14, 12), (13, 17)], fill=LR); i.line([(3, 22), (28, 22)], D); i.save('wefax')


def radiofax():
    i = Icon(); i.rect(3, 14, 28, 27, fill=L); i.rect(7, 4, 24, 15, fill=W); text_lines(i, 9, 7, 22, 3, D, 3)
    i.rect(6, 19, 25, 21, fill=K); antenna(i, 26, 2, 14); i.save('radiofax')


def sstv():
    i = Icon(); monitor(i, LC)
    i.rect(5, 12, 26, 16, fill=G, outline=None); i.poly([(8, 12), (13, 7), (18, 12)], fill=N, outline=None)
    i.ellipse(19, 6, 23, 10, fill=Y, outline=None); i.save('sstv')


def df():
    i = Icon(); i.ellipse(3, 3, 28, 28, fill=W); i.ellipse(7, 7, 24, 24, fill=None, outline=L)
    i.poly([(15, 5), (18, 15), (15, 17), (12, 15)], fill=LR); i.poly([(15, 26), (18, 16), (15, 17), (12, 16)], fill=D)
    i.save('df')


def snake():
    i = Icon(); i.rect(2, 2, 29, 29, fill=K)
    for (x, y) in [(5, 22), (9, 22), (13, 22), (13, 18), (13, 14), (17, 14), (21, 14)]:
        i.rect(x, y, x + 3, y + 3, fill=LG, outline=None)
    i.rect(21, 14, 24, 17, fill=Y, outline=None); i.rect(23, 6, 25, 8, fill=LR, outline=None); i.save('snake')


def pileup():
    i = Icon(); i.rect(4, 2, 27, 29, fill=K)
    cells = [(5, 25, LC), (9, 25, LC), (13, 25, Y), (17, 25, Y), (21, 25, LM), (5, 21, LR), (9, 21, LR), (17, 21, LM),
             (21, 21, LM), (13, 5, LG), (13, 9, LG), (17, 9, LG)]
    for (x, y, c) in cells:
        i.rect(x, y, x + 3, y + 3, fill=c)
    i.save('falling_blocks')


def music():
    i = Icon(); i.line([(12, 6), (12, 23)], K, 2); i.line([(24, 3), (24, 20)], K, 2)
    i.poly([(12, 6), (25, 3), (25, 7), (12, 10)], fill=K)
    i.ellipse(5, 20, 13, 27, fill=M); i.ellipse(17, 17, 25, 24, fill=M); i.save('music')


def bible():
    i = Icon(); i.rect(5, 3, 26, 28, fill=K); i.rect(7, 3, 26, 26, fill=N)
    i.rect(15, 7, 17, 20, fill=Y, outline=None); i.rect(11, 10, 21, 12, fill=Y, outline=None); i.save('bible')


def adsb():
    i = Icon(); i.ellipse(2, 2, 29, 29, fill=K, outline=G); i.ellipse(8, 8, 23, 23, fill=None, outline=G)
    i.line([(16, 16), (26, 8)], LG); i.poly([(9, 13), (13, 12), (14, 9), (15, 12), (19, 13), (15, 14), (15, 17), (13, 17), (13, 14)], fill=W, outline=None)
    i.save('adsb')


def rtty():
    i = Icon(); i.rect(3, 4, 28, 12, fill=W); glyph(i, 6, 6, 'RYRY', K)
    i.rect(2, 14, 29, 28, fill=N); i.rect(4, 17, 27, 25, fill=L)
    for r in range(2):
        for k in range(6):
            i.rect(5 + k * 4, 18 + r * 4, 7 + k * 4, 19 + r * 4, fill=K, outline=None)
    i.save('rtty')


def bbs():
    i = Icon(); i.rect(3, 9, 28, 26, fill=W); i.poly([(3, 9), (15, 19), (28, 9)], fill=L)
    antenna(i, 25, 1, 9); waves(i, 25, 3, LR, 2); i.save('bbs')


def propagation():
    i = Icon(); i.rect(2, 22, 29, 28, fill=G); i.d.arc([4, 4, 28, 40], 200, 340, fill=rgb(LB), width=2)
    i.d.arc([1, 0, 31, 36], 200, 340, fill=rgb(LM))
    antenna(i, 6, 15, 22); antenna(i, 26, 15, 22); i.save('propagation')


def repeaters():
    """A lattice tower with signal arcs either side of its top, centred on the mast."""
    i = Icon(); top = 10
    i.line([(16, top), (10, 29)], L); i.line([(16, top), (22, 29)], L)
    for y in (14, 20, 26):
        half = 1 + (y - top) * 6 // 21
        i.line([(16 - half, y), (16 + half, y)], L)
    i.line([(16 - 2, 14), (16 + 4, 20)], L); i.line([(16 + 4, 20), (16 - 5, 26)], L)
    i.line([(16, top - 3), (16, top)], L)
    i.rect(15, top - 4, 17, top - 2, fill=LR, outline=None)
    for r in (5, 9):
        i.d.arc([16 - r, top - 3 - r, 16 + r, top - 3 + r], 315, 45, fill=rgb(LR))
        i.d.arc([16 - r, top - 3 - r, 16 + r, top - 3 + r], 135, 225, fill=rgb(LR))
    i.save('repeaters')


def saver():
    """A monitor with a starfield on its black screen (computer batch 3A)."""
    i = Icon(); monitor(i, screen=K)
    for (x, y, c) in [(8, 7, W), (20, 6, L), (14, 10, W), (24, 12, D), (10, 15, L), (18, 16, W), (6, 11, D), (22, 9, W)]:
        i.px(x, y, c)
    i.rect(15, 12, 16, 13, fill=Y, outline=None); i.save('saver')


def scheduler():
    """A wall clock with a little bell on top (computer batch 3A)."""
    i = Icon(); i.ellipse(4, 6, 27, 29, fill=W); i.ellipse(6, 8, 25, 27, fill=None, outline=L)
    for (x, y) in [(15, 9), (15, 25), (7, 17), (23, 17)]:
        i.rect(x, y, x + 1, y + 1, fill=K, outline=None)
    i.line([(16, 18), (16, 11)], K, 2); i.line([(16, 18), (21, 21)], LR, 2)
    i.poly([(12, 5), (16, 1), (20, 5)], fill=Y); i.save('scheduler')


ALL = [edit, sysinfo, bandplan, install, setup, about, license_study, bookedit, logbook, freqman, programmer, solar,
       aprs, wefax, radiofax, sstv, df, snake, pileup, music, bible, adsb, rtty, bbs, propagation, repeaters, saver,
       scheduler]

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for make in ALL:
        make()
    # Every pixel must be one of the sixteen (the scheme recolouring matches them exactly).
    palette = {rgb(c)[:3] for c in (K, B, G, C, R, M, N, L, D, LB, LG, LC, LR, LM, Y, W)}
    for name in os.listdir(OUT):
        img = Image.open(os.path.join(OUT, name)).convert('RGBA')
        for xy in ((x, y) for y in range(img.height) for x in range(img.width)):
            p = img.getpixel(xy)
            if p[3] and p[:3] not in palette:
                raise SystemExit(name + ' has a colour outside the EGA palette: ' + str(p))
    print('wrote', len(ALL), 'icons to', OUT)
