#!/bin/bash
set -eu

feature_opts=""
data_dir="data/splits/med"
model_dir="experiments"
mkdir -p "${model_dir}"

timestamp=$(date '+%Y-%m-%dT%H:%M:%S%z')
gitid=$(git log -1 --format="%ci_%ce_%h" | perl -pe 's/ /T/; s/ //; s/ /_/g')
reports_dir="target/reports_run=${timestamp}_commit=${gitid}"
mkdir -p "${reports_dir}"
(cd $(dirname $reports_dir) && ln -sf $(basename $reports_dir) reports)
echo "REPORTS DIR: ${reports_dir}"

archive_dir=/cab0/brendano/www/semeval/reports

for formalism in "pas" "dm" "pcedt"
do
    model_name="${formalism}_med_model"
    model_file="${model_dir}/${model_name}"

    train_file="${data_dir}train.${formalism}.sdp"
    # train_file="lildata/liltrain.${formalism}.sdp"
    train_deps="${train_file}.dependencies"

    test_file="data/splits/sec20.${formalism}.sdp"
    test_deps="${test_file}.dependencies"

    pred_file="${model_file}.pred.${formalism}.sdp"
    trainpred_file="${model_file}.trainpred.${formalism}.sdp"

    set -x
    (
    ./java.sh lr.LRParser -mode train -saveEvery -1 \
      -formalism $formalism \
      -model ${model_file} -sdpInput ${train_file} -depInput ${train_deps} ${feature_opts}
    ./java.sh lr.LRParser -mode test \
      -formalism $formalism \
      -model ${model_file} -sdpOutput ${pred_file} -depInput ${test_deps} ${feature_opts}
    ./java.sh lr.LRParser -mode test \
      -formalism $formalism \
      -model ${model_file} -sdpOutput ${trainpred_file} -depInput ${train_deps} ${feature_opts}
    ./scripts/eval.sh "${test_file}" "${pred_file}" | tee "${reports_dir}/${model_name}.eval.log"
    ./scripts/eval.sh "${train_file}" "${trainpred_file}" | tee "${reports_dir}/${model_name}.train.eval.log"
    ./scripts/eval_to_csv.py < "${reports_dir}/${model_name}.eval.log" > "${reports_dir}/${model_name}.eval.csv"
    ./scripts/eval_to_csv.py < "${reports_dir}/${model_name}.train.eval.log" > "${reports_dir}/${model_name}.train.eval.csv"
    python errorAnalysis/confusionMatrix.py "${test_file}" "${pred_file}" > "${reports_dir}/${model_name}_confusion.html"
    python errorAnalysis/confusionMatrix.py "${train_file}" "${trainpred_file}" > "${reports_dir}/${model_name}_train_confusion.html"
    ) 2>&1 | tee -a ${reports_dir}/run.log

    set +x
done

(cd ${reports_dir} && awk '
  /including virtual/{x=1} 
  /excluding virtual/{x=0}
  /^L/ && x { print FILENAME,$0 }  
  ' *.eval.log | 
  perl -pe 's/://'  >  labeled_results.txt
)

echo -e "\n"
echo "****** FINAL F1 ******"
grep "LF" ${reports_dir}/labeled_results.txt
echo -e "\n"

echo "FINISHED: $reports_dir"
if [ -d "${archive_dir}" ]; then
  cp -r $reports_dir $archive_dir
  echo "Copied to $archive_dir/$reports_dir"
fi
