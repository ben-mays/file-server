#!/bin/bash

endpoint="$1"
fileId="$2"
filePath="$3"
tempPath="$4"

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

touch $tempPath
echo -e "[${CYAN}Verification${NC}] Uploading $fileId"
./upload-file.sh -e $endpoint -i $fileId $filePath
echo -e "[${CYAN}Verification${NC}] Finished uploading $fileId"
echo -e "[${CYAN}Verification${NC}] Retrieving $fileId"
./retrieve-file.sh $endpoint $fileId "useless-pass" $tempPath
echo -e "[${CYAN}Verification${NC}] Finished retrieving $fileId"

echo -e "[${CYAN}Verification${NC}] Calculating checksums"

originalchecksum=`shasum $filePath | cut -d " " -f1`
newchucksum=`shasum $tempPath | cut -d " " -f1`

if [ "$originalchecksum" == "$newchucksum" ]
then
    echo -e "[${CYAN}Verification${NC}] ${GREEN}No corruption!${NC} $originalchecksum = $newchucksum"
else
    echo -e "[${CYAN}Verification${NC}] ${RED}Data was corrupted!${NC} $originalchecksum != $newchucksum"
fi

rm -rf $tempPath
