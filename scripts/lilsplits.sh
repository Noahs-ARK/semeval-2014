#!/bin/sh
DATADIR="$(dirname $0)/../../data"

OUTPUT_DATADIR="lildata"
#OUTPUT_DATADIR="${DATADIR}/small_splits/"
#OUTPUT_DATADIR="${DATADIR}/medium_splits/"
mkdir -p "${OUTPUT_DATADIR}"

TRAIN_SKIP=250
#DEV_SKIP=50
#export TRAIN_SKIP=60
#export DEV_SKIP=12
#export TRAIN_SKIP=15
#export DEV_SKIP=3
# for f in dm pcedt pas; do
#for f in dm ; do
for f in pcedt ; do
 for ext in sdp sdp.dependencies; do
  cat "${DATADIR}/splits/train.${f}.${ext}" | awk 'BEGIN{s=0} /^#/{ s+=1 } s % '"${TRAIN_SKIP}"' == 0' > "${OUTPUT_DATADIR}/liltrain.${f}.${ext}"
#  cat "${DATADIR}/splits/dev.${f}.${ext}"   | awk 'BEGIN{s=0} /^#/{ s+=1 } s % '"${DEV_SKIP}"' == 0' > "${OUTPUT_DATADIR}/dev.${f}.${ext}"
 done
done
