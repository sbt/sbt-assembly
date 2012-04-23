#!/bin/bash
for i in 1 2 3; do
  zip -jr ../../lib/$i.jar $i
done
