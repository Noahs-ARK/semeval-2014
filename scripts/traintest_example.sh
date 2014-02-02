#!/bin/sh
set -eu


model_name="mymodel"
feature_opts=""
#feature_opts="-useIsEdgeFeature"
#feature_opts="-usePasLabelFeatures"
#feature_opts="-useIsEdgeFeature -usePasLabelFeatures"
data_dir="lildata/lil"
#data_dir="../data/splits/"
#data_dir="../data/medium_splits/"
experiments_dir="experiments"
#model_dir="${experiments_dir}/label_feats_medium"
model_dir="${experiments_dir}/label_feats_lil"
mkdir -p "${model_dir}"
model_file="${model_dir}/${model_name}"

formalism="pas"
train_file="${data_dir}train.${formalism}.sdp"
train_deps="${data_dir}train.${formalism}.sdp.dependencies"

test_file="${data_dir}dev.${formalism}.sdp"
test_deps="${data_dir}dev.${formalism}.sdp.dependencies"

pred_file="${model_file}.pred.dev.${formalism}.sdp"

set -x
./java.sh lr.LRParser -mode train \
  -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts}
./java.sh lr.LRParser -mode test \
  -model ${model_file} -sdpOutput ${pred_file} -depInput ${test_deps} ${feature_opts}
scripts/eval.sh ${test_file} ${pred_file}
