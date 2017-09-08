#!/bin/bash
set -eux

script=$(dirname $0)/coltree_to_sexpr.py

sdpfile=/cab1/corpora/LDC2013E167/dm.sdp
coltree=/cab1/corpora/LDC2013E167/companion/updated_3_8/sb.berkeley.cpn
sexpr=/cab1/corpora/LDC2013E167/companion/updated_3_8_postproc/sb.berkeley.cpn.sexpr
python $script $sdpfile $coltree > $sexpr

sdpfile=/cab1/corpora/LDC2013E167/test/dm.tt
coltree=/cab1/corpora/LDC2013E167/test/sb.berkeley.cpn
sexpr=/cab1/corpora/LDC2013E167/test/sb.berkeley.cpn.sexpr
python $script $sdpfile $coltree > $sexpr
