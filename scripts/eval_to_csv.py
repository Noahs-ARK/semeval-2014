#!/usr/bin/env python
import sys
from itertools import dropwhile, takewhile


def extract_prf(lines):
    # only care about labeled accuracy, including (virtual dependencies to) tops
    minus_preamble = dropwhile(lambda line: "including virtual dependencies" not in line, lines)
    relevant_lines = list(takewhile(lambda line: "excluding virtual dependencies" not in line, minus_preamble))

    p = [line.split()[1] for line in relevant_lines if line.startswith("LP:")][0]
    r = [line.split()[1] for line in relevant_lines if line.startswith("LR:")][0]
    f = [line.split()[1] for line in relevant_lines if line.startswith("LF:")][0]
    m = [line.split()[1] for line in relevant_lines if line.startswith("LM:")][0]
    return p, r, f, m


if __name__ == "__main__":
    p, r, f, m = extract_prf(sys.stdin)
    print('"LP", "LR", "LF", "LM"')
    print(", ".join('"{0}"'.format(x) for x in (p, r, f, m)))
