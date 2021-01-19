#!/bin/bash

black .
scalafmt
verible-verilog-format --inplace *.v
