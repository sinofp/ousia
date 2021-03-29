#!/bin/bash

black .
scalafmt || mill ousia.reformat
(
    GLOBIGNORE='Naive.v:board/**/ram*:board/**/pll*' # don't format Intel IP files
    verible-verilog-format --inplace {*.v,tb/*.v,board/**/*.v}
)
shfmt -i 4 -w .
prettier --parser yaml --write ousia.core .github/workflows/test.yml
