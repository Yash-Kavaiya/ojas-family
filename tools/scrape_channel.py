# Content seed, step 1: run in a scratch folder holding channel_playlists.txt (as playlists.txt); writes videos.json
import re, json, urllib.request, urllib.parse

def get(url):
    r = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Accept-Language': 'en'})
    return urllib.request.urlopen(r, timeout=30).read().decode('utf8')

TITLE = r'"title":\{"runs":\[\{"text":"((?:[^"\\]|\\.)*)"'

def vids(html):
    out = {}
    for kind in ('playlistVideoRenderer', 'videoRenderer'):
        for m in re.finditer(r'"' + kind + r'":\{"videoId":"([A-Za-z0-9_-]{11})".*?' + TITLE, html):
            out.setdefault(m.group(1), json.loads('"' + m.group(2) + '"'))
        if out:
            break
    return out

res = {}
for line in open('playlists.txt', encoding='utf8'):
    pid = line.split('|')[0].strip()
    res[pid] = vids(get('https://www.youtube.com/playlist?list=' + pid))
for q in ['blue light', 'blue healing', 'morning', 'night', 'box breathing', 'protection', 'yoga', 'pranayama',
          'trataka', 'gratitude', 'sleep', 'meditation', 'intuition', 'energy']:
    res['search:' + q] = vids(get('https://www.youtube.com/@OjasHealing/search?query=' + urllib.parse.quote(q)))
json.dump(res, open('videos.json', 'w', encoding='utf8'), ensure_ascii=False, indent=1)
for k, v in res.items():
    print(k, len(v))
