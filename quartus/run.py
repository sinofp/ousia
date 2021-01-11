#!/usr/bin/env python

from edalize import *
from sys import argv
import os

work_root = "."

files = [
    {"name": os.path.relpath("Naive.v", work_root), "file_type": "verilogSource"},
    # {"name": os.path.relpath("rom.v", work_root), "file_type": "verilogSource"},
    # {"name": os.path.relpath("rom.qip", work_root), "file_type": "QIP"},
]

tool = "quartus"

tool_options = {
    "quartus": {
        "family": "Cyclone 10 LP",
        "device": "10CL016YU256C8G",
    },
}

edam = {
    "files": files,
    "name": "ousia-naive",
    "tool_options": tool_options,
    "toplevel": "Naive",
}

backend = get_edatool(tool)(edam=edam, work_root=work_root)

if len(argv) == 1:
    backend.configure()
    backend.build()
elif len(argv) == 2:
    if argv[1] == "conf":
        backend.configure()
    else:
        backend.build()

