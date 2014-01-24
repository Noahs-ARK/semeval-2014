set -eu

modelfile=mymodel
predfile=mypred

trainfile=../data/splits/first100.dm.sdp
traindeps=../data/splits/first100.dm.sdp.dependencies

testfile=../data/splits/dev.dm.sdp
testdeps=../data/splits/dev.dm.sdp.dependencies

set -x
./java.sh lr.LRParser train $modelfile $trainfile $traindeps
./java.sh lr.LRParser test $modelfile $predfile $testdeps
echo "evalling"
scripts/eval.sh $testfile $predfile
