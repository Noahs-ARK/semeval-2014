FEATNAME=$1

./scripts/traintest_example_med.sh pas feats/$FEATNAME/pas
./scripts/traintest_example_med.sh dm feats/$FEATNAME/dm
./scripts/traintest_example_med.sh pcedt feats/$FEATNAME/pcedt
