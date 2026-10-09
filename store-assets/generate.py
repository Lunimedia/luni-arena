#!/usr/bin/env python3
"""Render original Luni brand artwork for the Play listing (requires Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = Path(__file__).resolve().parent
FONTS = [
    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
    'C:/Windows/Fonts/arialbd.ttf',
]


def font(size):
    for candidate in FONTS:
        if Path(candidate).is_file():
            return ImageFont.truetype(candidate, size)
    return ImageFont.truetype('DejaVuSans-Bold.ttf', size)


def gradient(width, height, upper, lower):
    image = Image.new('RGB', (width, height))
    px = image.load()
    for y in range(height):
        t = y / max(height - 1, 1)
        row = tuple(round(upper[k] * (1 - t) + lower[k] * t) for k in range(3))
        for x in range(width):
            px[x, y] = row
    return image.convert('RGBA')


def disk(canvas, box, color, stroke=0, outline=None):
    ImageDraw.Draw(canvas).ellipse(box, fill=color, outline=outline, width=stroke)


def emblem(size):
    scale = size / 512
    art = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    def rect(x0, y0, x1, y1, fill, radius=0):
        d.rounded_rectangle(tuple(round(v * scale) for v in (x0, y0, x1, y1)),
                            radius=round(radius * scale), fill=fill)
    def circle(cx, cy, r, fill):
        disk(art, tuple(round(v * scale) for v in (cx-r, cy-r, cx+r, cy+r)), fill)
    # Brand orbit and moon are original artwork; no game characters or covers.
    circle(256, 256, 233, '#6953aa')
    circle(256, 256, 214, '#ac85ff')
    circle(256, 256, 192, '#142243')
    circle(256, 256, 174, '#78e7e0')
    circle(256, 256, 155, '#192945')
    circle(284, 229, 123, '#f7f0ff')
    circle(334, 205, 112, '#192945')
    rect(122, 254, 244, 290, '#171b34', 7)
    rect(165, 211, 202, 334, '#171b34', 7)
    circle(324, 224, 23, '#ff84cb')
    circle(362, 273, 23, '#84eee5')
    circle(121, 130, 11, '#ffa567')
    circle(412, 366, 9, '#b897ff')
    return art


def icon():
    s = 1024
    canvas = gradient(s, s, (12, 18, 43), (20, 30, 66))
    glow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((123, 123, 901, 901), fill=(137, 88, 242, 90))
    canvas = Image.alpha_composite(canvas, glow.filter(ImageFilter.GaussianBlur(70)))
    canvas.alpha_composite(emblem(936), (44, 44))
    output = canvas.resize((512, 512), Image.Resampling.LANCZOS)
    output.save(OUT / 'icone-play-512.png', optimize=True)


def feature():
    w, h, zoom = 1024, 500, 2
    base = gradient(w*zoom, h*zoom, (17, 29, 65), (15, 18, 47))
    d = ImageDraw.Draw(base)
    for x in range(32, w*zoom, 86):
        for y in range(30, h*zoom, 86):
            d.ellipse((x,y,x+3,y+3), fill=(83, 104, 151, 110))
    glow = Image.new('RGBA', base.size, (0,0,0,0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((990, 0, 1990, 1000), fill=(146,91,239,100))
    base = Image.alpha_composite(base, glow.filter(ImageFilter.GaussianBlur(125)))
    d = ImageDraw.Draw(base)
    # Feature art extends the visual language of the icon without repeating it.
    d.ellipse((1240,80,1960,800), outline='#9c78ed', width=21)
    d.ellipse((1310,150,1890,730), outline='#78e7e0', width=11)
    d.ellipse((1380,220,1820,660), fill='#24345c', outline='#435c8f', width=5)
    nodes=[(1580,255,'#f4efff'),(1440,385,'#80e5db'),(1725,395,'#ff82cb'),
           (1480,580,'#bc96fa'),(1730,590,'#f5a371')]
    for a,b in [(0,1),(0,2),(1,3),(2,4),(3,4)]:
        d.line((nodes[a][0],nodes[a][1],nodes[b][0],nodes[b][1]), fill='#a89ed1', width=8)
    for x,y,color in nodes:
        d.ellipse((x-35,y-35,x+35,y+35),fill=color)
        d.ellipse((x-20,y-20,x+20,y+20),fill='#253055')
    d.rounded_rectangle((112,130,224,145), radius=7, fill='#87e8df')
    d.text((108,185), 'LUNI', font=font(143), fill='#faf4ff', stroke_width=1)
    d.text((105,345), 'ARENA', font=font(143), fill='#ac88ff', stroke_width=1)
    d.text((112,575), 'SUA BIBLIOTECA. SUA ARENA.', font=font(34), fill='#bce7e5')
    d.rounded_rectangle((112,724,779,731), radius=3, fill='#8c6af3')
    d.ellipse((98,755,114,771), fill='#ff82cb')
    d.ellipse((139,755,155,771), fill='#80e5db')
    base.convert('RGB').resize((w,h), Image.Resampling.LANCZOS).save(OUT / 'grafico-play-1024x500.png', optimize=True)


if __name__ == '__main__':
    icon()
    feature()
    print('Play artwork saved to', OUT)
