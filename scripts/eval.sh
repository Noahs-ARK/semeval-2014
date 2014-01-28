# first arg: gold
# second arg: pred

# sbt logs to stdout, and Evaluator prints to stderr. we only care about Evaluator
sbt "run-main sdp.tools.Evaluator ${1} ${2}" 3>&1 1>/dev/null 2>&3
