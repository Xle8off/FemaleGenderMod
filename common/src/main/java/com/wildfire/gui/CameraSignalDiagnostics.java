package com.wildfire.gui;

import java.util.Set;

/** Client-side allowlist too: upstream diagnostic text never reaches chat/UI. */
public final class CameraSignalDiagnostics {
    private static final Set<String> CODES=Set.of("UNAUTHORIZED","NOT_FOUND","DECODING_ERROR","INVALID_PARAMS","PAYLOAD_TOO_LARGE","SESSION_ERROR","TRACK_ERROR","TRACK_NOT_FOUND","TRACK_NAME_CONFLICT","INVALID_SESSION_DESCRIPTION","NOT_FOUND_TRACK_ERROR","EMPTY_TRACK_ERROR","REPEATED_LOCAL_TRACK_ERROR","UPDATE_TRACK_ERROR","CLOSE_TRACK_ERROR","RETRYABLE_TRANSIENT_ERROR","TEMPORARILY_UNAVAILABLE_ERROR","TRANSPORT_UNAVAILABLE_ERROR","BACKEND_ERROR","OTHER_ERROR","HTTP_ERROR","TIMEOUT","DNS_FAILED","CONNECT_FAILED","TLS_FAILED","IO_ERROR","INTERRUPTED","INVALID_JSON","INVALID_RESPONSE","RESPONSE_TOO_LARGE","MID_MISMATCH","INTERNAL_ERROR");
    private CameraSignalDiagnostics(){}
    public static String format(String stage,String code,int http){
        String step=switch(stage){case "SESSION_CREATE"->"Создание SFU-сессии";case "PUBLISH_TRACK"->"Публикация видеотрека";case "SUBSCRIBE_TRACK"->"Подписка на видео";case "ANSWER"->"Ответ WebRTC";case "TRACK_CLOSE"->"Закрытие трека";case "SESSION_READ"->"Проверка SFU-сессии";default->"Координатор камер";};
        return step+(http>=100&&http<=599?" · HTTP "+http:"")+" · "+(CODES.contains(code)?code:"OTHER_ERROR");
    }
}
