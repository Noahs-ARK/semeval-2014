DATADIR="/cab1/corpora/LDC2013E167/"

for f in dm pcedt pas; do
 for ext in sdp sdp.dependencies; do
  cat ${DATADIR}${f}.${ext} | awk 'BEGIN{s=0} /^#/{ s+=1 } s <= 27200' > ${DATADIR}splits/train.${f}.${ext} # 80% train
  cat ${DATADIR}${f}.${ext} | awk 'BEGIN{s=0} /^#/{ s+=1 } s > 27200 && s <= 30600' > ${DATADIR}splits/dev.${f}.${ext} # 10% dev
  cat ${DATADIR}${f}.${ext} | awk 'BEGIN{s=0} /^#/{ s+=1 } s > 30600' > ${DATADIR}splits/test.${f}.${ext} # 10% test
 done
done
