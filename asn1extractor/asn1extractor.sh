#!/usr/bin/env sh

java -cp classes-dex2jar.jar:classes2-dex2jar.jar:target/asn1extractor-1.0-SNAPSHOT.jar net.heberling.ismart.reverseengineering.asn1extractor.ASN1Extractor "$@"
