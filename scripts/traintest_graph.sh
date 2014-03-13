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
#test_file="data/splits/sec20.${formalism}.sdp"
test_file="${data_dir}dev.${formalism}.sdp"
test_deps="${test_file}.dependencies"

pred_file="${model_file}.pred.${formalism}.sdp"

set -x
./java.sh amr.SemanticParser -mode train -labelset labels.dm \
  -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts} | tail -n +6 > ${model_file}
./java.sh amr.SemanticParser -mode test -labelset labels.dm -goldSingletons \
  -model ${model_file} -sdpInput ${test_file} -sdpOutput ${pred_file} -depInput ${test_deps} ${feature_opts} > ${pred_file}
scripts/eval.sh ${test_file} ${pred_file}
