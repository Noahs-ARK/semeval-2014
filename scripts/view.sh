#!/bin/bash
set -eu
python $(dirname $0)/view.py < $1 > $1.html
open $1.html
