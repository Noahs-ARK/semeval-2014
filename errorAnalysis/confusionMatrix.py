import sys,operator,os
import numpy as np
from collections import Counter

'''
Print confusion matrix for predictions vs. gold data.  Usage:

python confusionMatrix.py gold/sec20.pcedt.sdp pred/feb7_recsplit.pcedt.pred > output/pcedt.html

'''

# annotation iterator from brendan's view.py
def iter_sents(filename):
	file=open(filename)
	cur = []
	sentid = None
	for line in file:
		line=line.strip()
		if not line:
			yield sentid,cur
			sentid=None
			cur=[]
		if len(line.split())==1 and line.startswith("#"):
			sentid = line.lstrip("#")
			cur=[]
			continue
		row = line.split('\t')
		cur.append(row)
	if cur:
		yield sentid,cur

	file.close()

gold=enumerate(iter_sents(sys.argv[1]))
pred=enumerate(iter_sents(sys.argv[2]))

zipped=zip(gold,pred)

confusion={}
topcounts=np.zeros((2,2))
predcounts=np.zeros((2,2))

for (gold, pred) in zipped:
	numsent,(sentid,rows)=gold
	numsent_p,(sentid_p,rows_p)=pred

	T = len(rows)
	K = len(rows[0])
	K_pred = len(rows_p[0])

	if K < 5: continue

	topmarkers = [row[4] for row in rows]
	topmarkers_pred = [row[4] for row in rows_p]

	top_triggers = [i for i in range(T) if topmarkers[i]=='+']
	top_triggers_pred = [i for i in range(T) if topmarkers_pred[i]=='+']

	predmarkers= [row[5] for row in rows]
	predmarkers_pred= [row[5] for row in rows_p]
	
	pred_triggers = [i for i in range(T) if predmarkers[i]=='+']
	pred_triggers_pred = [i for i in range(T) if predmarkers_pred[i]=='+']

	for i in range(T):
		top_g=int(i in top_triggers)
		top_p=int(i in top_triggers_pred)
		topcounts[top_g, top_p]+=1

		p_g=int(i in pred_triggers)
		p_p=int(i in pred_triggers_pred)
		predcounts[p_g, p_p]+=1


	goldVals=np.empty( (T,T), dtype="S50")
	goldVals.fill("_")

	predVals=np.empty( (T,T), dtype="S50")
	predVals.fill("_")


	for i in range(T):
		for j in range(6,K):
			val=rows[i][j]
			if val != "_":
				goldVals[i, pred_triggers[j-6]]=val
	
	for i in range(T):
		for j in range(6,K_pred):
			val=rows_p[i][j]
			if val != "_":
				predVals[i, pred_triggers_pred[j-6]]=val

	for i in range(T):
		for j in range(T):
			gval=goldVals[i,j]
			pval=predVals[i,j]

			if gval not in confusion:
				confusion[gval]=Counter()

			confusion[gval][pval]+=1

totals={}
targetTotal=0
precTotals=Counter()

for key in confusion:
	for tag in confusion[key]:
		precTotals[tag]+=confusion[key][tag]

	totals[key]=sum(confusion[key].values())
	if key != "_":
		targetTotal+=totals[key]

sorted_tuple = sorted(totals.iteritems(), key=operator.itemgetter(1), reverse=True)
sortedKeys=[]
for (k,v) in sorted_tuple:
	sortedKeys.append(k)

print """<html><style>td
{
padding:3px;
} tr:nth-child(even) {
    background-color: #EEEEEE;
    }</style>"""

print "<h1>%s confusion matrix</h1><hr />" % os.path.basename(sys.argv[2])

topPrec=topcounts[1,1]/(topcounts[0,1] + topcounts[1,1])
topRecall=topcounts[1,1]/(topcounts[1,0] + topcounts[1,1])
topF=(2*topPrec*topRecall)/(topPrec+topRecall)

print "<h3>Tops</h3>" 
print "Gold n = %d; predicted n = %d" % (topcounts[1,0] + topcounts[1,1], topcounts[0,1] + topcounts[1,1])
print """
<table>
<tr><td>P</td><td>R</td><td>F</td></tr>
<tr><td>%.3f</td><td>%.3f</td><td>%.3f</td></tr>
</table>
""" %(topPrec, topRecall, topF)


predPrec=predcounts[1,1]/(predcounts[0,1] + predcounts[1,1])
predRecall=predcounts[1,1]/(predcounts[1,0] + predcounts[1,1])
predF=(2*predPrec*predRecall)/(predPrec+predRecall)

print "<hr />"

# print "<h3>Predicates</h3>"
# print "Gold n = %d; predicted n = %d" % (predcounts[1,0] + predcounts[1,1], predcounts[0,1] + predcounts[1,1])

# print """
# <table>
# <tr><td>P</td><td>R</td><td>F</td></tr>
# <tr><td>%.3f</td><td>%.3f</td><td>%.3f</td></tr>
# </table>
# """ %(predPrec, predRecall, predF)

# print "<hr />"

print "<h3>Labels</h3>"

print "Gold = rows, Predicted = columns. Sorted by count of the number of gold tags in the test data (the most important quadrant is the upper left).  Calculated from the correct vs. predicted label for each pair of tokens in the sentence. High values for ['_', LABEL] and [LABEL, '_'] probably implies that we're getting the label right but the head wrong."
print """
<p /><table>
<tr><td bgcolor="#FA58AC" width=30px></td><td>BAD +50% of correct label misattributed to this one predicted category</td></tr>
<tr><td bgcolor="#FFFF00" width=30px></td><td>BAD +30%</td></tr>
<tr><td bgcolor="#01DF01" width=30px></td><td>BAD +10%</td></tr>
<tr><td bgcolor="#A9BCF5" width=30px></td><td>GOOD +80% right!</td></tr>
</table>"""
print "<body><p /><table border=1>"
print "<tr><td><em>cumulative%%</em></td><td><em>count</em><td>P</td><td>R</td><td>F</td></td><td></td><td>%s</td></tr>" % '</td><td>'.join(sortedKeys)

runningTotal=0.
for tag in sortedKeys:
	#print tag
	total=totals[tag]
	if tag != "_":
		runningTotal+=total
	recall=float(confusion[tag][tag])/total
	precision=0
	if precTotals[tag] > 0:
		precision=float(confusion[tag][tag])/precTotals[tag]
	F1=0
	if precision+recall > 0:
		F1=(2*precision*recall)/(precision+recall)
	print "<tr>"
	if tag != "_":
		print "<td>%.3f</td>" % (runningTotal/targetTotal)
		print "<td>%d</td>" % totals[tag]
		print "<td>%.3f</td>" % precision
		print "<td>%.3f</td>" % recall
		print "<td>%.3f</td>" % F1
	else:
		print "<td></td><td></td><td></td><td></td><td></td>"
	print "<td>%s</td>" % tag
	for key in sortedKeys:
		val=""
		color=""
		if confusion[tag][key] > 0:
			val=str(confusion[tag][key])
			ratio=float(confusion[tag][key])/total
			if key == tag:
				if ratio > .8 and tag != "_":
					color="#A9BCF5"
			elif ratio > .5:
				color="#FA58AC"
			elif ratio > .3:
				color="#FFFF00"
			elif ratio > .2:
				color="#01DF01"
		print "<td bgcolor=\"%s\">%s</td>" % (color, val),
	print "<td>%s</td></tr>" % tag
print "<table></body></html>"

