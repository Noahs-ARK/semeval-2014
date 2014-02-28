# first arg: gold
# second arg: pred

# sbt logs to stdout, and Evaluator prints to stderr. we only care about Evaluator
(`dirname $0`/sbt "run-main sdp.tools.Evaluator ${1} ${2}" 3>&1 1>/dev/null 2>&3) > eval.$$

## If you want to see the real output
# cat eval.$$; exit

## Pared-down output
cat eval.$$ | awk '
/file/{print} 
/including.*dependencies/{x=1} /Labeled scores/{y=1; print}
x && y && /:/{print}
/Unlabeled scores/{exit}
'

echo "\nFull report: eval.$$"
