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