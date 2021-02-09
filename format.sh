#!/bin/bash

black .
scalafmt
verible-verilog-format --inplace *.v
shfmt -i 4 -w .
prettier --parser yaml --write ousia.core .github/workflows/test.yml
