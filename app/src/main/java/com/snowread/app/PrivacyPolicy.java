package com.snowread.app;
/** Only presentation choices may enter the atmosphere section of exported/restored settings. */
final class PrivacyPolicy {
    static final String[] WEATHER_CACHE={"auto","lat","lon","city","coordAt","syncAt","autoOk","autoDesc","weatherAuto","temp","intensity"};
    static boolean keepSetting(String group,String key){
        if("atmosphere".equals(group))return "enabled".equals(key)||"weather".equals(key)||"time".equals(key);
        return !("listen".equals(group)&&"allowNetworkVoice".equals(key));
    }
}
