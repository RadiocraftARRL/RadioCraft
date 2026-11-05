"""Converts the World English Bible (public domain, ebible.org eng-web_vpl.zip) gospels into RadioCraft's resource.
Usage: python -I bible_convert.py <eng-web_vpl.zip> <out .txt.gz>
Output (UTF-8, ASCII in practice, gzipped): '#B <code> <name>' then '<chapter>:<verse> <text>' lines."""
import sys, zipfile, re, gzip, collections, html
BOOKS = [('MAT', 'Matthew'), ('MRK', 'Mark'), ('LUK', 'Luke'), ('JHN', 'John')]
ASCII = {'\u2019': "'", '\u2018': "'", '\u201c': '"', '\u201d': '"', '\u2014': '--', '\u2013': '-', '\u2026': '...', '\u00a0': ' '}
z = zipfile.ZipFile(sys.argv[1])
xml = z.read('eng-web_vpl.xml').decode('utf-8-sig')
verses = collections.OrderedDict((b, []) for b, _ in BOOKS)
odd = collections.Counter()
for m in re.finditer(r'<v b="([A-Z0-9]+)" c="(\d+)" v="(\d+)">(.*?)</v>', xml, re.S):
    b, c, v, t = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
    if b not in verses:
        continue
    t = html.unescape(t)
    for k, r in ASCII.items():
        t = t.replace(k, r)
    t = re.sub(r'\s+', ' ', t).strip()
    for ch in t:
        if ord(ch) > 126:
            odd[ch] += 1
    verses[b].append((c, v, t))
lines = []
for code, name in BOOKS:
    lines.append('#B %s %s' % (code, name))
    for c, v, t in verses[code]:
        lines.append('%d:%d %s' % (c, v, t))
data = ('\n'.join(lines) + '\n').encode('utf-8')
with gzip.GzipFile(sys.argv[2], 'wb', mtime=0) as g:
    g.write(data)
print({b: (len(vs), max(c for c, _, _ in vs)) for b, vs in verses.items()}, len(data), 'bytes; non-ASCII left:', dict(odd))
