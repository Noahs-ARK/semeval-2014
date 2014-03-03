#!/bin/sh
set -eu

model_name="mymodel"
feature_opts=""
model_dir="bladir"
mkdir -p "${model_dir}"
model_file="${model_dir}/${model_name}"

formalism="pas"
data_dir="lildata/lil"
train_file="${data_dir}train.${formalism}.sdp"
train_deps="${train_file}.dependencies"

# sec20 files are from: /cab1/corpora/LDC2013E167/splits
test_file="data/splits/sec20.${formalism}.sdp"
test_deps="${test_file}.dependencies"

pred_file="${model_file}.pred.${formalism}.sdp"

set -x
./java.sh lr.LRParser -mode train \
  -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts}
./java.sh lr.LRParser -mode test \
  -model ${model_file} -sdpOutput ${pred_file} -depInput ${test_deps} ${feature_opts}
scripts/eval.sh ${test_file} ${pred_file}
