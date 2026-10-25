package com.wildfire.gui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Only our message composer uses the emoji formatter; vanilla text areas are untouched. */
public final class FormattedChatInput {
    private static final Set<Object> INPUTS=Collections.newSetFromMap(new WeakHashMap<>());
    private FormattedChatInput(){}
    public static void register(Object input){INPUTS.add(input);}
    public static boolean contains(Object input){return INPUTS.contains(input);}
}
