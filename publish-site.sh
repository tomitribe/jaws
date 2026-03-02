#!/bin/bash
set -e

rm -rf site
zensical build
ghp-import -n -p site

echo "Published to https://tomitribe.github.io/jaws/"
