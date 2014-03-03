import sys

"""
Convert sdp format to "thebeast" format for importing into WhatsWrongWithMyNLP
(https://code.google.com/p/whatswrong/)

Usage:

python ../converter.py < feb7_recsplit.dm.pred > pred.dm.beast

"""


for numsent, (sentid,rows) in enumerate(sdputils.iter_sents()):
	T = len(rows)
	K = len(rows[0])
	if K < 5: continue
	print ">>\n>Word"
	for i in range(T):
		print "%d\t\"%s\"" % (i, rows[i][1])

	topmarkers = [row[4] for row in rows]
	predmarkers= [row[5] for row in rows]
	
	pred_triggers = [i for i in range(T) if predmarkers[i]=='+']

	print "\n>Spans"
	for i in range(T):
		if topmarkers[i] == "+":
			print "%s\t%s\t\"%s\"" % (i, i, "TOP")
		if predmarkers[i] == "+":
			print "%s\t%s\t\"%s\"" % (i, i, "PRED")

	print "\n>Relations"
	for i in range(T):
		for j in range(6,K):

			val=rows[i][j]

			if val != "_":
				print "%s\t%s\t\"%s\"" % (pred_triggers[j-6], i, val)




