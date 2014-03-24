#!/bin/sh
set -eu

model_name="mymodel"
feature_opts=""
model_dir="bladir"
mkdir -p "${model_dir}"
model_file="${model_dir}/${model_name}"

formalism="pcedt"
data_dir="lildata/lil"
train_file="${data_dir}train.${formalism}.sdp"
train_deps="${train_file}.dependencies"

# sec20 files are from: /cab1/corpora/LDC2013E167/splits
test_file="data/splits/sec20.${formalism}.sdp"
test_deps="${test_file}.dependencies"

pred_file="${model_file}.pred.${formalism}.sdp"
outputFeatsToFile="featsForRF"

word_vectors="resources/word_vectors_norm.txt"

set -x
./java.sh lr.LRParser -mode train \
  -formalism $formalism \
  -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts} -wordVectors ${word_vectors} -outputFeatsToFile ${outputFeatsToFile}
./java.sh lr.LRParser -mode test \
  -formalism $formalism \
  -model ${model_file} -sdpInput ${test_file} -depInput ${test_deps} ${feature_opts} -wordVectors ${word_vectors}
scripts/eval.sh ${test_file} ${pred_file}
