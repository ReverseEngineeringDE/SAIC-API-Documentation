package net.heberling.ismart.reverseengineering.asn1extractor;

import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Boolean;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Element;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Enum;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1EnumItem;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Integer;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1OctetString;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Sequence;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1SequenceOf;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1String;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.constraints.ASN1SizeConstraint;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.constraints.ASN1ValueRangeConstraint;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class ASN1Extractor {
    public static void main(String[] args) throws ClassNotFoundException {
        if (args.length < 2) {
            System.err.println("Missing parameters.");
            System.err.println("Usage: asn1extractor <ASN1 Module name> <included classes>");
            System.exit(-1);
        }
        String moduleName = args[0];

        List<Class<?>> classes = new LinkedList<>();
        for (int i = 1; i < args.length; i++) {
            classes.add(ASN1Extractor.class.getClassLoader().loadClass(args[i]));
        }

        String sequenceDefinition = generateASN1Module(moduleName, classes);
        System.out.println(sequenceDefinition);
    }

    static String generateASN1Module(String name, List<Class<?>> classes) {
        StringBuilder sequenceDefinition = new StringBuilder();
        sequenceDefinition
                .append(name)
                .append("\n")
                .append("\n")
                .append("DEFINITIONS\n")
                .append("AUTOMATIC TAGS ::= \n")
                .append("BEGIN\n");
        getASN1Definition(sequenceDefinition, new HashSet<>(), new LinkedList<>(classes));
        sequenceDefinition.append("\nEND");
        return sequenceDefinition.toString();
    }

    private static void getASN1Definition(
            StringBuilder sequenceDefinition, Set<Class<?>> processed, Queue<Class<?>> todo) {
        if (todo.isEmpty()) {
            return;
        }
        Class<?> aClass = todo.remove();
        if (processed.contains(aClass)) {
            return;
        }

        processed.add(aClass);

        if (aClass.isAnnotationPresent(ASN1Enum.class)) {
            ASN1Enum sequence = aClass.getAnnotation(ASN1Enum.class);
            sequenceDefinition.append(sequence.name());
            sequenceDefinition.append(" ::= ENUMERATED\n" + "{\n");
            try {
                String collect =
                        Arrays.stream(
                                        aClass.getDeclaredField("value")
                                                .getType()
                                                .getDeclaredFields())
                                .filter(f -> f.isAnnotationPresent(ASN1EnumItem.class))
                                .sorted(
                                        Comparator.comparingInt(
                                                o -> o.getAnnotation(ASN1EnumItem.class).tag()))
                                .map(
                                        f ->
                                                "  "
                                                        + f.getAnnotation(ASN1EnumItem.class).name()
                                                        + "("
                                                        + f.getAnnotation(ASN1EnumItem.class).tag()
                                                        + ")")
                                .collect(Collectors.joining(",\n"));
                sequenceDefinition.append(collect);

            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
            sequenceDefinition.append("\n}\n");

        } else {

            ASN1Sequence sequence = aClass.getAnnotation(ASN1Sequence.class);

            String name = sequence.name();
            if (name.startsWith("mp")) {
                // must be upper case...
                name = "MP" + name.substring(2);
            }
            sequenceDefinition.append(name);
            sequenceDefinition.append(" ::= SEQUENCE\n" + "{\n");
            String collect =
                    Arrays.stream(aClass.getDeclaredFields())
                            .filter(field -> field.isAnnotationPresent(ASN1Element.class))
                            .sorted(
                                    Comparator.comparingInt(
                                            o -> o.getAnnotation(ASN1Element.class).tag()))
                            .map(
                                    f -> {
                                        StringBuilder definition = new StringBuilder("  ");
                                        definition.append(
                                                f.getAnnotation(ASN1Element.class).name());
                                        if (f.isAnnotationPresent(ASN1SequenceOf.class)) {
                                            definition.append(" SEQUENCE SIZE");
                                            addSizeConstraint(f, definition, false);
                                            definition.append(" OF");
                                            // TODO: we only support Sequences for now, but the
                                            // standard also allows primitive types and enums
                                            Class<?> t =
                                                    (Class<?>)
                                                            ((ParameterizedType) f.getGenericType())
                                                                    .getActualTypeArguments()[0];
                                            definition
                                                    .append(" ")
                                                    .append(
                                                            t.getAnnotation(ASN1Sequence.class)
                                                                    .name());
                                            todo.add(t);
                                        } else {

                                            boolean sizeConstraints = false;
                                            if (f.isAnnotationPresent(ASN1Boolean.class)) {
                                                definition.append(" BOOLEAN");
                                            } else if (f.isAnnotationPresent(ASN1Integer.class)) {
                                                definition.append(" INTEGER");
                                            } else if (f.isAnnotationPresent(
                                                    ASN1OctetString.class)) {
                                                definition.append(" OCTET STRING");
                                                sizeConstraints = true;
                                            } else if (f.isAnnotationPresent(ASN1String.class)) {
                                                ASN1String s = f.getAnnotation(ASN1String.class);
                                                switch (s.stringType()) {
                                                    case 18:
                                                        definition.append(" NumericString");
                                                        break;
                                                    case 22:
                                                        definition.append(" IA5String");
                                                        break;
                                                    default:
                                                        throw new RuntimeException(
                                                                "Unsupported string type for "
                                                                        + f
                                                                        + ": "
                                                                        + s.stringType());
                                                }
                                                sizeConstraints = true;
                                            } else if (f.getType()
                                                    .isAnnotationPresent(ASN1Sequence.class)) {
                                                definition
                                                        .append(" ")
                                                        .append(
                                                                f.getType()
                                                                        .getAnnotation(
                                                                                ASN1Sequence.class)
                                                                        .name());
                                                todo.add(f.getType());
                                            } else if (f.getType()
                                                    .isAnnotationPresent(ASN1Enum.class)) {
                                                definition
                                                        .append(" ")
                                                        .append(
                                                                f.getType()
                                                                        .getAnnotation(
                                                                                ASN1Enum.class)
                                                                        .name());
                                                todo.add(f.getType());
                                            } else {
                                                throw new RuntimeException(
                                                        "No type annotation found for "
                                                                + f
                                                                + " in: "
                                                                + Arrays.asList(
                                                                        f.getAnnotations()));
                                            }

                                            addSizeConstraint(f, definition, sizeConstraints);
                                        }
                                        if (f.getAnnotation(ASN1Element.class).isOptional()) {
                                            definition.append(" OPTIONAL");
                                        }

                                        return definition.toString();
                                    })
                            .collect(Collectors.joining(",\n"));

            sequenceDefinition.append(collect);
            sequenceDefinition.append("\n}\n");
        }
        while (!todo.isEmpty()) {
            getASN1Definition(sequenceDefinition, processed, todo);
        }
    }

    private static void addSizeConstraint(
            Field f, StringBuilder definition, boolean finalSizeConstraints) {
        Optional.ofNullable(f.getAnnotation(ASN1ValueRangeConstraint.class))
                .ifPresent(
                        c -> {
                            if (finalSizeConstraints) {
                                definition.append("(SIZE");
                            }
                            definition
                                    .append("(")
                                    .append(c.min())
                                    .append("..")
                                    .append(c.max())
                                    .append(")");
                            if (finalSizeConstraints) {
                                definition.append(")");
                            }
                        });
        Optional.ofNullable(f.getAnnotation(ASN1SizeConstraint.class))
                .ifPresent(
                        c -> {
                            if (finalSizeConstraints) {
                                definition.append("(SIZE");
                            }
                            definition.append("(").append(c.max()).append(")");
                            if (finalSizeConstraints) {
                                definition.append(")");
                            }
                        });
    }
    //    public static void main(String[] args) throws Exception {
    //
    //        Config.setP();
    //
    //
    //
    ////         MP_OTARequest r1 =
    // MPRequestUtil.sendRequest("00F7111007900C82C60C183060C183060C183060C183060C183060C183060C183060C183060C183062C183060C183060CDAB96AD99B270E31346D996E668DB2AD68E72E55B85AB3CAB593160D5CB56791AE6CCC7263CCDDAB366C65508DC020200468ACF134468ACF1342468ACF1342468ACF1342000000000100A0", Config.BASE_URL);
    //      //  MP_OTARequest r2 =
    // MPRequestUtilV2.sendRequest("1008F207B0030303030303030303030303030303030FFF983060C183060C183060C183060C183060C183060C183060C183060C183060C18B060C183060C18336AE5AB666C9C38C4D1B665B99A36CAB5A39CB956E16ACF2AD64C583572D59E46B9B331C98F3376AC58CCA72A2B268C1CB59D1D83572D59B301C65508DC0000000000000000000000023280A0000002", Config.BASE_URL_2_1);
    //
    //
    //        //  new ASN1Extractor().enqueueNewLogin("param1","param2","param3",1);
    //
    //        String res =
    // "0139111009A00C82F60C183060C183060C183060C183060C183060C183060C183060C183060C1830608F0F3C99A061E79332EC9970E36EC7263C38DBE164C9A62C2D70B0C8D1B6460E313570E5CB1CCC98B76CCD8B36ED9AB062C655697C040202468ACF1343530ECA864468ACF1342468ACF1342000000000100A75460EAA34329030B1B1B7BAB73A1034B9903737BA103932B3B4B9BA32B932B2170";
    //
    //// res="abcd"+res;
    //        final MP_OTARequest receive = new MPAdapterServiceImpl().receive(res.getBytes());
    //
    //        // new OTADecoder<>(new ByteArrayInputStream(new byte[])).decode()
    //
    // //       MP_OTARequest request =
    // MPRequestUtil.sendRequest("00F7111007900C82C60C183060C183060C183060C183060C183060C183060C183060C183060C183062C183060C183060CDAB96AD99B270E31346D996E668DB2AD68E72E55B85AB3CAB593160D5CB56791AE6CCC7263CCDDAB366C65508DC020200468ACF134468ACF1342468ACF1342468ACF1342000000000100A0", Config.BASE_URL);
    //
    //       // MP_UserLoggingInResp resp = new LoginHttp(null).decoder(request);
    //
    //        System.out.println("Hello world!");
    //
    //        MP_UserLoggingInReq paramMP_userLoggingInReq = new MP_UserLoggingInReq();
    //
    // paramMP_userLoggingInReq.setDeviceId("eZ6dHNqlRgKb2YfZ3plBIr:APA91bHRf_Iq1T6ThOkhMm691A8UtlEaiN1bpoJU7z0ZfJBBZryRCQraNXK5gSZStU46vcxfQMZYH3bbFmIiaf5vUjsCWE3TxFpNdaPo0H4MAs9DwqBMNH4MbRtd8hYIHHtwkKwbNpdL###europecar");
    //        paramMP_userLoggingInReq.setPassword("test1234");
    //
    //        String str = MPRequestUtil.encodeRequest(paramMP_userLoggingInReq, "501",
    // "0000000000000000000000000000000000000#test@test.de", null, null, null);
    //        String str1_1 = MPRequestUtilV11.encodeRequest(paramMP_userLoggingInReq, "501",
    // "0000000000000000000000000000000000000#test@test.de", null, null, null);
    //        String str2 = MPRequestUtilV2.encodeRequest(paramMP_userLoggingInReq, "501",
    // "0000000000000000000000000000000000000#test@test.de", null, null, null);
    //
    //
    //        MP_OTARequest test = new LoginHttp(null).login(paramMP_userLoggingInReq,
    // "0000000000000000000000000000000000000#test@test.de");
    //
    //        System.out.println(test);
    //
    //    }
    //
    //    public MP_UserLoggingInReq enqueueNewLogin(String paramString1, String password, String
    // paramString3, int paramInt) {
    //        MP_UserLoggingInReq mP_UserLoggingInReq = new MP_UserLoggingInReq();
    //        mP_UserLoggingInReq.setPassword(password);
    //       // boolean bool = TextUtils.isEmpty(FirebaseInstanceId.getInstance().getToken());
    //        String str =
    // "cqSHOMG1SmK4k-fzAeK6hr:APA91bGtGihOG5SEQ9hPx3Dtr9o9mQguNiKZrQzboa-1C_UBlRZYdFcMmdfLvh9Q_xA8A0dGFIjkMhZbdIXOYnKfHCeWafAfLXOrxBS3N18T4Slr-x9qpV6FHLMhE9s7I6s89k9lU7DD";
    ////        if (bool) {
    ////            password =
    // "cqSHOMG1SmK4k-fzAeK6hr:APA91bGtGihOG5SEQ9hPx3Dtr9o9mQguNiKZrQzboa-1C_UBlRZYdFcMmdfLvh9Q_xA8A0dGFIjkMhZbdIXOYnKfHCeWafAfLXOrxBS3N18T4Slr-x9qpV6FHLMhE9s7I6s89k9lU7DD";
    ////        } else {
    ////            password = FirebaseInstanceId.getInstance().getToken();
    ////        }
    //        StringBuilder stringBuilder3 = new StringBuilder();
    //        stringBuilder3.append(str);
    //        stringBuilder3.append("###europecar");
    //        LogUtils.d("deviceId: ", stringBuilder3.toString());
    ////        if (TextUtils.isEmpty(password))
    ////            password = str;
    //        StringBuilder stringBuilder2 = new StringBuilder();
    //        stringBuilder2.append(password);
    //        stringBuilder2.append("###europecar");
    //        mP_UserLoggingInReq.setDeviceId(stringBuilder2.toString());
    //
    //        enqueueHttp((IASN1PreparedElement) mP_UserLoggingInReq, "501", getNewUid(paramString1,
    // paramString3, paramInt), null, null, null, Config.BASE_URL, new RxMpHttpCallBack() {
    //            @Override
    //            public void onFailly(Throwable paramThrowable, int paramInt) {
    //
    //            }
    //
    //            @Override
    //            public void onFinish() {
    //
    //            }
    //
    //            @Override
    //            public void onPreviousStepOfFailure(Throwable paramThrowable, int paramInt, String
    // paramString) {
    //
    //            }
    //
    //            @Override
    //            public void onResponse(MP_OTARequest paramMP_OTARequest) {
    //
    //            }
    //
    //            @Override
    //            public void onStart() {
    //
    //            }
    //        });
    //
    //        return null;
    //    }
    //
    //    public <T> void enqueueHttp(IASN1PreparedElement paramIASN1PreparedElement, final String
    // applicationId, String paramString2, String paramString3, Long paramLong, String paramString4,
    // String paramString5, final RxMpHttpCallBack rxMpHttpCallBack) {
    //        StringBuilder stringBuilder = new StringBuilder();
    //        stringBuilder.append("RxMPHttpFactory 请求的参数 \nRxMPHttpFactory:IASN1PreparedElement =
    // ");
    //      //  stringBuilder.append(JsonUtil.toString(paramIASN1PreparedElement));
    //        stringBuilder.append(" ,applicationId = ");
    //        stringBuilder.append(applicationId);
    //        stringBuilder.append(" \nuid =");
    //        stringBuilder.append(paramString2);
    //        stringBuilder.append(" ,vin = ");
    //        stringBuilder.append(paramString3);
    //        stringBuilder.append(" \neventId = ");
    //        stringBuilder.append(paramLong);
    //        stringBuilder.append(" ,token = ");
    //        stringBuilder.append(paramString4);
    //        stringBuilder.append(" \nurl = ");
    //        stringBuilder.append(paramString5);
    //        LogUtils.printHttp(stringBuilder.toString());
    //
    //            String str = MPRequestUtilV11.encodeRequest(paramIASN1PreparedElement,
    // applicationId, paramString2, paramString3, paramLong, paramString4);
    /// *
    //            Observer<MP_OTARequest> observer = new Observer<MP_OTARequest>() {
    //                public void onComplete() {
    //                    rxMpHttpCallBack.onFinish();
    //                }
    //
    //                public void onError(Throwable param1Throwable) {
    //                    rxMpHttpCallBack.onPreviousStepOfFailure(param1Throwable, -203,
    // applicationId);
    //                    rxMpHttpCallBack.onFailly(param1Throwable, -203);
    //                    rxMpHttpCallBack.onFinish();
    //                }
    //
    //                public void onNext(MP_OTARequest param1MP_OTARequest) {
    //                    StringBuilder stringBuilder;
    //                    if (param1MP_OTARequest == null) {
    //                        String str = RxMPHttpFactory.this.context.getString(2131690353);
    //                        stringBuilder = new StringBuilder();
    //                        stringBuilder.append("http response  error! MP_OTARequest is null
    // ,applicationId = ");
    //                        stringBuilder.append(applicationId);
    //                        LogUtils.e("网络请求  RxMPHttpFactory", stringBuilder.toString());
    //                        rxMpHttpCallBack.onPreviousStepOfFailure(new Throwable(str), -203,
    // applicationId);
    //                        rxMpHttpCallBack.onFailly(new Throwable(str), -203);
    //                    } else if (stringBuilder.getDispatcherBody() == null) {
    //                        String str = RxMPHttpFactory.this.context.getString(2131690353);
    //                        stringBuilder = new StringBuilder();
    //                        stringBuilder.append("http response  error! getDispatcherBody is null
    // ,applicationId = ");
    //                        stringBuilder.append(applicationId);
    //                        LogUtils.e("网络请求  RxMPHttpFactory", stringBuilder.toString());
    //                        rxMpHttpCallBack.onPreviousStepOfFailure(new Throwable(str), -202,
    // applicationId);
    //                        rxMpHttpCallBack.onFailly(new Throwable(str), -202);
    //                    } else if (stringBuilder.getDispatcherBody().getResult().intValue() == 0)
    // {
    //                        StringBuilder stringBuilder1 = new StringBuilder();
    //                        stringBuilder1.append("onResponse ,applicationId = ");
    //                        stringBuilder1.append(applicationId);
    //                        LogUtils.e("网络请求  RxMPHttpFactory", stringBuilder1.toString());
    //                        rxMpHttpCallBack.onResponse((MP_OTARequest)stringBuilder);
    //                    } else if (stringBuilder.getDispatcherBody().getResult().intValue() ==
    // -204) {
    //                        StringBuilder stringBuilder1 = new StringBuilder();
    //                        stringBuilder1.append("客户端 发送请求失败 Code:");
    //                        stringBuilder1.append(stringBuilder.getDispatcherBody().getResult());
    //                        stringBuilder1.append(" ,applicationId = ");
    //                        stringBuilder1.append(applicationId);
    //                        LogUtils.e("网络请求  RxMPHttpFactory", stringBuilder1.toString());
    //                        rxMpHttpCallBack.onPreviousStepOfFailure(new Throwable(""),
    // stringBuilder.getDispatcherBody().getResult().intValue(), applicationId);
    //                        rxMpHttpCallBack.onFailly(new Throwable(""),
    // stringBuilder.getDispatcherBody().getResult().intValue());
    //                    } else {
    //                        MessageBinder messageBinder;
    //                        String str2 = RxMPHttpFactory.this.context.getString(2131690353);
    //                        String str1 = str2;
    //                        if (stringBuilder.getDispatcherBody().getErrorMessage() != null)
    //                            try {
    //                                str1 = new String();
    //                                this(stringBuilder.getDispatcherBody().getErrorMessage(),
    // "utf-8");
    //                            } catch (UnsupportedEncodingException
    // unsupportedEncodingException) {
    //                                StringBuilder stringBuilder2 = new StringBuilder();
    //                                stringBuilder2.append("requestErrorMsg
    // UnsupportedEncodingException :");
    //
    // stringBuilder2.append(unsupportedEncodingException.getMessage());
    //                                stringBuilder2.append("  result ");
    //
    // stringBuilder2.append(stringBuilder.getDispatcherBody().getResult());
    //                                stringBuilder2.append(" ,applicationId = ");
    //                                stringBuilder2.append(applicationId);
    //                                LogUtils.e("网络请求  RxMPHttpFactory",
    // stringBuilder2.toString());
    //                                str1 = str2;
    //                            }
    //                        StringBuilder stringBuilder1 = new StringBuilder();
    //                        stringBuilder1.append("后台 返回错误信息:");
    //                        stringBuilder1.append(str1);
    //                        stringBuilder1.append(" Code:");
    //                        stringBuilder1.append(stringBuilder.getDispatcherBody().getResult());
    //                        stringBuilder1.append(" ,applicationId = ");
    //                        stringBuilder1.append(applicationId);
    //                        LogUtils.e("网络请求  RxMPHttpFactory", stringBuilder1.toString());
    //                        if
    // (CommonUtils.tokenBeOverdue(stringBuilder.getDispatcherBody().getResult().intValue())) {
    //                            messageBinder = new
    // MessageBinder("vma.token.failure.show.dialog");
    //                            messageBinder.setMessage(str1);
    //                            EventBus.getDefault().post(messageBinder);
    //                        } else {
    //                            rxMpHttpCallBack.onPreviousStepOfFailure(new Throwable(str1),
    // messageBinder.getDispatcherBody().getResult().intValue(), applicationId);
    //                            rxMpHttpCallBack.onFailly(new Throwable(str1),
    // messageBinder.getDispatcherBody().getResult().intValue());
    //                        }
    //                    }
    //                }
    //
    //                public void onSubscribe(Disposable param1Disposable) {
    //                    rxMpHttpCallBack.onStart();
    //                }
    //            };
    //            Observable.just(str).map(new
    // _$$Lambda$RxMPHttpFactory$XaeM2Zxvvc_oJU7cAz_f9l2ijWk(applicationId,
    // paramString5)).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(observer);
    // */
    //    }
    //
    //
    //    public static String getNewUid(String paramString1, String paramString2, int paramInt) {
    //        if (paramInt == 1) {
    //            StringBuilder stringBuilder1 = new StringBuilder();
    //            stringBuilder1.append("#");
    //            stringBuilder1.append(paramString2);
    //            stringBuilder1.append("-");
    //            stringBuilder1.append(paramString1);
    //            paramString1 = stringBuilder1.toString();
    //        } else if (paramString1.length() < 50) {
    //            StringBuilder stringBuilder1 = new StringBuilder();
    //            stringBuilder1.append("#");
    //            stringBuilder1.append(paramString1);
    //            paramString1 = stringBuilder1.toString();
    //        }
    //        int i = paramString1.length();
    //        StringBuffer stringBuffer = new StringBuffer();
    //        for (paramInt = 0; paramInt < 50 - i; paramInt++)
    //            stringBuffer.append("0");
    //        StringBuilder stringBuilder = new StringBuilder();
    //        stringBuilder.append(stringBuffer.toString());
    //        stringBuilder.append(paramString1);
    //        return stringBuilder.toString();
    //    }
}
