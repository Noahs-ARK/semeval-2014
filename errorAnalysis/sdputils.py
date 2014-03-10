# annotation iterator from brendan's view.py
import sys


def iter_sents(filename=None):
    in_file = open(filename) if filename else sys.stdin

    cur = []
    sentid = None
    for line in in_file:
        line = line.strip()
        if not line:
            yield sentid, cur
            sentid = None
            cur = []
        if len(line.split()) == 1 and line.startswith("#"):
            sentid = line.lstrip("#")
            cur = []
            continue
        row = line.split('\t')
        cur.append(row)
    if cur:
        yield sentid, cur
    if filename:
        in_file.close()
