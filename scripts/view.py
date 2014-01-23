#!/usr/bin/env python
import sys

def iter_sents():
    cur = []
    sentid = None
    for line in sys.stdin:
        line=line.strip()
        if not line:
            yield sentid,cur
            sentid=None
            cur=[]
        if len(line.split())==1 and line.startswith("#"):
            sentid = line.lstrip("#")
            cur=[]
            continue
        row = line.split('\t')
        cur.append(row)
    if cur:
        yield sentid,cur

print """
    <meta content="text/html; charset=utf-8" http-equiv="Content-Type"/>
    """

for numsent, (sentid,rows) in enumerate(iter_sents()):
    if numsent>10: break

    tokids = [row[0] for row in rows]
    words  = [row[1] for row in rows]
    lemmas = [row[2] for row in rows]
    poses  = [row[3] for row in rows]
    topmarkers = [row[4] for row in rows]
    predmarkers= [row[5] for row in rows]
    T = len(rows)

    # zero-indexed
    pred_triggers = [i for i in range(T) if predmarkers[i]=='+']
    assert len(rows[0])-6 == len(pred_triggers)

    # print sentid
    # print predmarkers

    header_info = ['','word','lemma','pos','istop','ispred']
    for i in pred_triggers:
        header_info.append("%s:%d" % (words[i], i+1))

    print "#" + sentid
    # print "<table>"
    print "<table cellpadding=3 border=1 cellspacing=0 width='100%'>"
    print "<tr>", ' '.join(["<th>%s" % x for x in header_info])
    for row in rows:
        print "<tr>", ' '.join(["<td>%s" % x for x in row])
    print "</table>"


