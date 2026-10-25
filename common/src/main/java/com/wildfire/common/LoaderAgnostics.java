package com.wildfire.common;

import java.nio.file.Path;

public interface LoaderAgnostics {
   LoaderAgnostics INSTANCE = WildfireHelper.getService(LoaderAgnostics.class);

   Path getConfigDir();

   boolean isDevelopmentEnv();

   String getModVersion(String var1);

   boolean onClient();
}
