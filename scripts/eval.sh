# first arg: gold
# second arg: pred
sbt "run-main sdp.tools.Evaluator ${1} ${2}" 3>&1 1>/dev/null 2>&3
