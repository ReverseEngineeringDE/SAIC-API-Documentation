#!/usr/bin/env sh

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

java -cp ${SCRIPT_DIR}/classes-dex2jar.jar:${SCRIPT_DIR}/classes2-dex2jar.jar:${SCRIPT_DIR}/target/asn1extractor-0.0.0-SNAPSHOT.jar net.heberling.ismart.reverseengineering.asn1extractor.ASN1Extractor "$@"
