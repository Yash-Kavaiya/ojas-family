# Content seed, step 2: run next to videos.json -> python build_content_seed.py <repo>/firebase/functions/scripts/content.json
"""Builds the content seed: hand-picked Ojas Healing videos per section, plus whole playlists."""
import json, re, sys, urllib.request, urllib.parse
import xml.etree.ElementTree as ET

scraped = json.load(open('videos.json', encoding='utf8'))
titles = {}
for group in scraped.values():
    titles.update(group)


def get(url):
    r = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Accept-Language': 'en'})
    return urllib.request.urlopen(r, timeout=30).read().decode('utf8')


def feed(playlist):
    """Playlist RSS: newest 15 videos, oldest first so parts stay in order."""
    ns = {'a': 'http://www.w3.org/2005/Atom', 'yt': 'http://www.youtube.com/xml/schemas/2015'}
    root = ET.fromstring(get('https://www.youtube.com/feeds/videos.xml?playlist_id=' + playlist))
    out = []
    for e in root.findall('a:entry', ns):
        vid = e.find('yt:videoId', ns).text
        title = e.find('a:title', ns).text
        titles[vid] = title
        out.append(vid)
    return list(reversed(out))


def playlist(pid):
    # The scrape (up to 100, in playlist order) when it worked; otherwise the RSS feed
    ids = feed(pid)  # the official feed; the scraped pages mixed playlists up
    # Series ("Part 3 of 30", "Video - 5 -", "4 of 75") read in order
    def part(v):
        m = re.search(r'(?:Part|Video)\s*[–—-]?\s*(\d+)|(\d+)\s*of\s*\d+|[–—-]\s*(\d+)\s*[–—-]', titles.get(v, ''))
        return int(next(g for g in m.groups() if g)) if m else 10_000
    return sorted(ids, key=part)


def search(q):
    """Channel search for a topic (law of attraction etc.)."""
    html = get('https://www.youtube.com/@OjasHealing/search?query=' + urllib.parse.quote(q))
    out = []
    for m in re.finditer(r'"videoRenderer":\{"videoId":"([A-Za-z0-9_-]{11})".*?"title":\{"runs":\[\{"text":"((?:[^"\\]|\\.)*)"', html):
        if m.group(1) not in out:
            titles[m.group(1)] = json.loads('"' + m.group(2) + '"')
            out.append(m.group(1))
    return out


CHAKRA_SERIES = ['p5-ovlXzzC8', 'RwgpCuK40HM', 'ezZxQhGn7Dg', '0ysCoiIUN-A', 'gkzQ1fJma7Q', 'IgZVpuxF6Lc', 'iEAtO5VHMA8']

PATANJALI = 'PLKlWeums7nbWqjJKim5JnK9t4rHaFWIqO'
SELF_HEALING = 'PLKlWeums7nbUqA-GU2SSOGC1Az2ytRwUj'
SHIVA_SUTRAS = 'PLKlWeums7nbUh_zDR0_dPVFEOK0amA7xc'
PROTECTION = 'PLKlWeums7nbXyVnUELcYYiO6I01Cj9sop'
ENCYCLOPEDIA = 'PLKlWeums7nbUpitKaBuZ2v77tk3Tb7kRU'
BODY_CHAKRAS = 'PLKlWeums7nbXDvI2wc6bSD6a1_V54-8QY'
MEDITATION = 'PLKlWeums7nbU7E8PuE3UWYE0z2TYHU_Kq'
VBT = 'PLKlWeums7nbXjKQ-bycZgNAVHNwANtmm-'
QA = 'PLKlWeums7nbUH7Vco70Ywtd008gAuGAIc'
WHAT_IS_PROTECTION = 'PRDkHzFEFVI'

# Dict order is priority: a video shared by two playlists stays in the first section listed
sections = {
    # 2.3 Morning: protection, breathing, energy increase
    'morning': ['cBvGVnbIcd0', 'dIwfc_KoA80', 'tfzP5-tiRLk'],
    # 2.4 Night: relaxing, thanksgiving, intuition
    'night': ['3uTcoQMRvMI', 'Ijk5dIrYip8', '539voldcAjs', 'Kycy971Rj-g'],
    # 2.1 Blue Healing Light general meditation (open to all)
    'blue_light': ['LbMOk3yjgns', 'ovLzhvLYVcA', '-1QSOUvHouM', 'TMo1ArI7Kuk'],
    # 2.2 Quick protection: the Protection Vidhi series (clearing energy)
    'quick_protection': [v for v in playlist(PROTECTION) if v != WHAT_IS_PROTECTION] + ['Q4TrtqqKiGg'],
    # 2.5 Daily yoga, pranayama and tratak; Patanjali's Yoga Sutras
    'yoga': ['aU9ldHo4Ugg', '9v7RXlfPJio'] + playlist(PATANJALI) + ['Wu_vU6YhcMM'],
    # 2.6 Other: chakra meditations, all about meditation, self healing course, Shiva Sutras, Vigyan Bhairav Tantra
    'other': CHAKRA_SERIES + playlist(MEDITATION) + playlist(SELF_HEALING) + playlist(SHIVA_SUTRAS) + playlist(VBT),
    # 4.1 Understanding: what is protection, energy, law of attraction, Spiritual Encyclopedia, body chakras, Q&A
    'understanding': [WHAT_IS_PROTECTION, 'veaDVmgWEZU', 'ttKt6THQgzo']
                     + [v for v in search('law of attraction') if 'attraction' in titles[v].lower()][:3]
                     + playlist(ENCYCLOPEDIA) + playlist(BODY_CHAKRAS) + playlist(QA),
}

seen = set()
docs = []
for section, ids in sections.items():
    order = 0
    for vid in ids:
        if vid in seen or vid not in titles:
            continue  # a video lives in one section only (doc id yt_<videoId>)
        seen.add(vid)
        docs.append({'videoId': vid, 'section': section, 'title': titles[vid][:200], 'order': order})
        order += 1

json.dump(docs, open(sys.argv[1], 'w', encoding='utf8'), ensure_ascii=False, indent=1)
for s in sections:
    print(s, sum(1 for d in docs if d['section'] == s))
print('total', len(docs))
