#!/bin/bash

totalAgents=$SYSTEM_TOTALJOBSINPHASE
agentNumber=$SYSTEM_JOBPOSITIONINPHASE

totalAgents=4
agentNumber=2

if [[ $totalAgents -eq 0 ]]; then totalAgents=1; fi
if [ -z "$agentNumber" ]; then agentNumber=1; fi

echo $totalAgents
echo $agentNumber

tests_to_skip=$(find . -name "*Test.java" | sed -e 's#^.*src/test/java/\(.*\)\.java#\1#' | tr "/" ".")
its_to_skip=$(find . -name "*IT.java" | sed -e 's#^.*src/test/java/\(.*\)\.java#\1#' | tr "/" "." )

tests_to_skip_filename="tests_to_skip.txt"
ITs_to_skip_filename="ITs_to_skip.txt"

tests_to_skip_file=$(Pipeline.Workspace)/$tests_to_skip_filename
ITs_to_skip_file=$(Pipeline.Workspace)/$ITs_to_skip_filename

counter=0;
for i in $tests_to_skip; do
   echo Counter is $counter
   if [[ $counter -ne $agentNumber ]]; then
      echo "$i"\n >> $tests_to_skip_file
   fi
   counter=$((counter+1))
   if [[ $counter -gt $totalAgents ]]; then counter=1; fi
done

counter=0;
for i in $tests_to_skip; do
   echo Counter is $counter
   if [[ $counter -ne $agentNumber ]]; then
      echo "$i"\n >> $ITs_to_skip_file
   fi
   counter=$((counter+1))
   if [[ $counter -gt $totalAgents ]]; then counter=1; fi
done

echo "##vso[task.setvariable variable=testExclusionFile]$tests_to_skip_file"
echo "##vso[task.setvariable variable=itExclusionFile]$ITs_to_skip_file"
