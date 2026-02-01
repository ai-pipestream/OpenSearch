#!/bin/bash
set -e

# Setup python environment
if [ ! -d "venv" ]; then
    echo "Creating python virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    pip install grpcio grpcio-tools protobuf
else
    source venv/bin/activate
fi

# Run the python demo script
echo "Running metrics demo..."
export PYTHONPATH=$PYTHONPATH:$(pwd)
python3 demo_metrics.py
