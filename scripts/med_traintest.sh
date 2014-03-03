#!/bin/sh
set -eu

feature_opts=""
data_dir="data/splits/med"
model_dir="experiments"
mkdir -p "${model_dir}"
reports_dir="target/reports"
mkdir -p "${reports_dir}"

for formalism in "pas" "dm" "pcedt"
do
    model_name="${formalism}_med_model"
    model_file="${model_dir}/${model_name}"

    train_file="${data_dir}train.${formalism}.sdp"
    train_deps="${train_file}.dependencies"

    test_file="data/splits/sec20.${formalism}.sdp"
    test_deps="${test_file}.dependencies"

    pred_file="${model_file}.pred.${formalism}.sdp"

    set -x
    ./java.sh lr.LRParser -mode train \
      -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts}
    ./java.sh lr.LRParser -mode test \
      -model ${model_file} -sdpOutput ${pred_file} -depInput ${test_deps} ${feature_opts}
    scripts/eval.sh ${test_file} ${pred_file} > "${reports_dir}/${model_name}.log"
    python errorAnalysis/confusionMatrix.py "${test_file}" "${pred_file}" > "${reports_dir}/${model_name}_confusion.html"
done
