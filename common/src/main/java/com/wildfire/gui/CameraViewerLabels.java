package com.wildfire.gui;

/** Render only controlled states/codes, never transport exceptions, SDP or ICE addresses. */
public final class CameraViewerLabels {
    private CameraViewerLabels(){}
    public static String status(String value,long age){
        if(age>8000&&!value.equals("ERROR"))return "Нет свежего подтверждения";
        return switch(value){case "CONNECTING"->"Подключается";case "CONNECTED"->"Соединён, ждём кадры";case "RECEIVING"->"Получает видео";case "NO_FRAMES"->"Соединён, но кадров нет";case "ERROR"->"Ошибка подключения";default->"Приём не подтверждён";};
    }
    public static String error(String code){return switch(code){
        case "WARMUP_TIMEOUT"->"Камера не начала передачу вовремя";
        case "SUBSCRIBE_FAILED"->"Координатор не создал подписку";case "PEER_INIT_FAILED"->"Не запустился WebRTC";
        case "ANSWER_FAILED"->"Не подготовлен ответ WebRTC";case "ANSWER_REJECTED"->"Координатор отклонил ответ";
        case "ICE_FAILED"->"Не установилось соединение с SFU";case "CONNECT_TIMEOUT"->"Истекло время подключения";default->"";
    };}
}
