r"""
INPUTS -- "coltree" format

#20001001
NNP     (S(NP(NP*
NNP     *)
,       *
CD      (ADJP(NP*
NNS     *)
JJ      *)
,       *)
MD      (VP*
VB      (VP*
DT      (NP*
NN      *)
IN      (PP*
DT      (NP*
JJ      *
NN      *))
NNP     (NP*
CD      *)))
.       *)



OUTPUTS:
#20001001 \t SEXPR
"""

import sys,os,re

# http://www.cis.upenn.edu/~treebank/tokenization.html
ptb_escape_table = {
        '(': '-LRB-',
        ')': '-RRB-',
        '{': '-LCB-',
        '}': '-RCB-',
        '[': '-LSB-',
        ']': '-RSB-',
}

def ptb_escape(tag):
    for lhs,rhs in ptb_escape_table.items():
        tag = tag.replace(lhs,rhs)
    return tag

def yield_sentid_and_lines(filename):
    cur = []
    sentid = None
    for line in open(filename):
        line = line.rstrip('\n')
        if not line.strip(): continue
        if line.startswith('#2') and len(line.strip().split())==1:
            if cur: yield sentid,cur
            sentid = line.strip()
            cur = []
            continue
        cur.append(line)
    if cur: yield sentid,cur

##########################################


sdp_file = sys.argv[1]  ## Needed to get tokens
coltree_file = sys.argv[2]

sdp_sents = list(yield_sentid_and_lines(sdp_file))
coltree_sents = list(yield_sentid_and_lines(coltree_file))

# print sdp_sents
# print sdp_sents[0]

tokens_by_sentid = {sentid: [line.split('\t')[1] for line in lines] for sentid,lines in sdp_sents} 
# print tokens_by_sentid

for sentid,lines in coltree_sents:
    tokens = tokens_by_sentid[sentid]
    if not len(tokens)==len(lines):
        print "UHOH ||| %s ||| %s" % (sentid, repr(tokens))
        print '\n'.join(lines)
        assert False

    if lines[0]=='_\t_':
        print "%s\tNOPARSE" % sentid
        continue

    new_sexpr = ""
    for i,line in enumerate(lines):
        pos,star_fragment = line.split('\t')
        assert sum(int(char=='*') for char in star_fragment)==1, line
        ss = star_fragment
        escaped_pos = ptb_escape(pos)
        escaped_token = ptb_escape(tokens[i])
        ss = ss.replace("*", "(%s %s)" % (escaped_pos, escaped_token))
        ss = ss.replace('(', ' (').replace(')',') ')
        new_sexpr += " " + ss

    # tree = parsetools.parse_sexpr(new_sexpr)

    new_sexpr = new_sexpr.strip()
    new_sexpr = re.sub(r'\s+',' ', new_sexpr)
    for itr in range(100):
        s2 = new_sexpr.replace("( (","((").replace(") )","))")
        if s2 == new_sexpr: break
        new_sexpr = s2
    print "%s\t%s" % (sentid, new_sexpr)
