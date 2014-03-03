#!/bin/sh
set -eu

formalism=$1
model_dir=$2

model_name="mymodel"
feature_opts=""
mkdir -p "${model_dir}"
model_file="${model_dir}/${model_name}"


train_file="data/fullsplits/medtrain.${formalism}.sdp"
train_deps="data/fullsplits/medtrain.${formalism}.sdp.dependencies"

# sec20 files are from: /cab1/corpora/LDC2013E167/splits
test_file="data/splits/sec20.${formalism}.sdp"
test_deps="data/splits/sec20.${formalism}.sdp.dependencies"

pred_file="${model_file}.med.pred.${formalism}.sdp"

set -x
./java.sh lr.LRParser -mode train \
  -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts}
./java.sh lr.LRParser -mode test \
  -model ${model_file} -sdpOutput ${pred_file} -depInput ${test_deps} ${feature_opts}
scripts/eval.sh ${test_file} ${pred_file}

python errorAnalysis/confusionMatrix.py ${test_file} ${pred_file} > ${model_file}.output.html