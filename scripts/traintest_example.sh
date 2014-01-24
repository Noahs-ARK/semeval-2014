set -eu

modelfile=$1
predfile=$2

trainfile=../data/tiny_split/pcedt.sdp.first100
traindeps=../data/tiny_split/pcedt.sdp.dependencies.first100

testfile=../data/tiny_split/pcedt.sdp.last1000
testdeps=../data/tiny_split/pcedt.sdp.dependencies.last1000

./java.sh lr.LRParser train $modelfile $trainfile $traindeps
./java.sh lr.LRParser test $modelfile $predfile $testdeps
echo "evalling"
scripts/eval.sh $testfile $predfile
