set -eu

modelfile=mymodel
predfile=mypred

trainfile=lildata/liltrain.dm.sdp
traindeps=lildata/liltrain.dm.sdp.dependencies

testfile=lildata/lildev.dm.sdp
testdeps=lildata/lildev.dm.sdp.dependencies

set -x
./java.sh lr.LRParser train $modelfile $trainfile $traindeps
./java.sh lr.LRParser test $modelfile $predfile $testdeps
echo "evalling"
scripts/eval.sh $testfile $predfile
