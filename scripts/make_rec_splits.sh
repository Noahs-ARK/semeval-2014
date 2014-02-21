# http://alt.qcri.org/semeval2014/task8/index.php?id=evaluation
# Following our recommended split of the training data, we then trained the graph-based parser of Bohnet (2010) on Sections 00-19 of the (tree reduction of our) SDP data, and applied the resulting âsyntacticâ parsing model to Section 20.

DATADIR="/cab1/corpora/LDC2013E167/"

for f in dm pcedt pas; do
 for ext in sdp sdp.dependencies; do
   cat ${DATADIR}/${f}.${ext} |
      awk -v dir=${DATADIR}splits -v myext=${f}.${ext} '
        BEGIN { print "outputting to dir=" dir " and extension=" myext }
        /^#2[0-1]/ {out="sec0019"} 
        /^#220/    {out="sec20"}
        /^#22[^0]/ {out=0} 
        out {
          print $0 > (dir "/" out "." myext)
        }'
 done
done
