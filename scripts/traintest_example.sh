set -eu

modelfile=mymodel
predfile=mypred

trainfile=lildata/liltrain.dm.sdp
traindeps=lildata/liltrain.dm.sdp.dependencies

testfile=lildata/lildev.dm.sdp
testdeps=lildata/lildev.dm.sdp.dependencies

set -x
./java.sh lr.LRParser -mode train \
  -model $modelfile -sdpInput $trainfile -depInput $traindeps
./java.sh lr.LRParser -mode test \
  -model $modelfile -sdpOutput $predfile -depInput $testdeps
scripts/eval.sh $testfile $predfile
