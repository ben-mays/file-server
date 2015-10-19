#!/bin/bash

endpoint=$1
output-path=$2
password=$3

curl -v 'http://localhost:8081/file/Everest.avi' -H "file-password: $password" > $output-path