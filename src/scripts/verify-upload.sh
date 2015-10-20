#!/bin/bash

endpoint="$1"
fileId="$2"
filePath="$3"
tempPath="$4"

touch $tempPath

./upload-file.sh -e $endpoint -i $fileId $filePath
./retrieve-file.sh $endpoint $fileId "useless-pass" $tempPath

originalchecksum=`shasum $filePath | cut -d " " -f1`
newchucksum=`shasum $tempPath | cut -d " " -f1`

if [ "$originalchecksum" == "$newchucksum" ]
then
    echo "$originalchecksum = $newchucksum"
else
    echo "$originalchecksum != $newchucksum"
fi

rm -rf $tempPath
