# ASN1Extractor
Tool to extarct ASN.1 schema definitions from annotated java classes.

## Build
Download the ismart.apk and extract the `classes.dex` and `classes2.dex` files.
Convert both files to jars with dex2jar.

place the files `classes-dex2jar.jar` and `classes-dex2jar.jar` in this directory.

Build the project with

```shell
mvn clean install
```

## Usage

Extract a single sequence with all dependencies into a file

```shell
./asn1extractor.sh MP_DispatcherBody com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.dispatcher.MP_DispatcherBody > MP_DispatcherBody.asn1
```

Extract multiple sequences with all dependencies into a file

```shell
./asn1extractor.sh ApplicationDataMP com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.MP_UserLoggingInReq com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.MP_UserLoggingInResp > ApplicationDataMP.asn1
```
