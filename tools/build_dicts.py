#!/usr/bin/env python3
"""Builds the keyboard word lists from hermitdave/FrequencyWords (OpenSubtitles 2018, MIT code / CC-BY-SA-4.0 content).
usage: tools/build_dicts.py ar_50k.txt en_50k.txt   -> app/src/main/assets/dict/{ar,en}.txt  (word<TAB>count)"""
import re, sys, unicodedata
ar_in, en_in = sys.argv[1], sys.argv[2]
OUT = "app/src/main/assets/dict/"
# words we never want to *suggest* or auto-correct to
BLOCK_EN = set("fuck fucking fucked fucker fuckin shit shitty bitch bitches asshole dick dicks cock pussy cunt whore slut bastard nigger nigga faggot fag retard retarded motherfucker bullshit horseshit dammit goddamn".split())
BLOCK_AR = set("كس زب طيز شرموط شرموطه عرص منيوك متناك قحبه قحبة نيك نيكني منيك".split())

def clean_ar(w):
    w = re.sub(r"[ً-ٰٟـ]", "", w)
    return w if re.fullmatch(r"[ء-ي]{1,15}", w) else None

def clean_en(w):
    w = w.lower()
    if not re.fullmatch(r"[a-z]+(?:'[a-z]+)?", w): return None
    if len(w) == 1 and w not in ("a", "i"): return None
    return w if len(w) <= 20 else None

def build(path, cleaner, block, limit, name):
    agg = {}
    for line in open(path, encoding="utf-8"):
        p = line.split()
        if len(p) != 2: continue
        w = cleaner(p[0]);
        if not w or w in block: continue
        agg[w] = agg.get(w, 0) + int(p[1])
    items = sorted(agg.items(), key=lambda x: -x[1])[:limit]
    with open(OUT + name, "w", encoding="utf-8") as f:
        for w, c in items: f.write(f"{w}\t{c}\n")
    print(name, len(items), items[:5])
build(ar_in, clean_ar, BLOCK_AR, 40000, "ar.txt")
build(en_in, clean_en, BLOCK_EN, 40000, "en.txt")
