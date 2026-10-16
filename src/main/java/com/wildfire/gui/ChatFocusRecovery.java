package com.wildfire.gui;
import java.util.function.Consumer;
import java.util.function.Supplier;
/** Recover only after a normal focus request was rejected by a compatibility override. */
public final class ChatFocusRecovery {
    private ChatFocusRecovery(){}
    public static <T> boolean ensure(T target,Supplier<T> current,Consumer<T> request,
                                    Consumer<T> direct,Consumer<T> blur){
        if(current.get()==target)return false;
        request.accept(target);
        if(current.get()==target)return false;
        T previous=current.get();
        if(previous!=null)blur.accept(previous);
        direct.accept(target);
        return true;
    }
}
