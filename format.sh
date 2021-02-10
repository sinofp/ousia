#!/bin/bash

black .
scalafmt
(
    GLOBIGNORE='ram*:pll*' # don't format Intel IP files
    verible-verilog-format --inplace *.v
)
shfmt -i 4 -w .
prettier --parser yaml --write ousia.core .github/workflows/test.yml
