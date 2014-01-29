DATADIR="data"

# for f in dm pcedt pas; do
for f in dm ; do
 for ext in sdp sdp.dependencies; do
  cat ${DATADIR}/splits/train.${f}.${ext} | awk 'BEGIN{s=0} /^#/{ s+=1 } s % 250 == 0' > ${DATADIR}/splits/liltrain.${f}.${ext}
  cat ${DATADIR}/splits/dev.${f}.${ext}   | awk 'BEGIN{s=0} /^#/{ s+=1 } s % 50 == 0' > ${DATADIR}/splits/lildev.${f}.${ext}
done
done
