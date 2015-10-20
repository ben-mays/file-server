#!/bin/bash

endpoint=$1
id=$2
password=$3
outputPath=$4

uri="$endpoint/file/$id"
cmd="curl -X GET -s $uri"

if [ -n $password ]
then
    cmd="$cmd -H 'file-password: $password'"
fi

if [ -n $outputPath ]
then
    echo "$outputPath"
    cmd="$cmd -o $outputPath"
fi

$cmd
