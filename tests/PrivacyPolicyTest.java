package com.snowread.app;
public final class PrivacyPolicyTest {
    static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("privacy check "+checks);}
    public static void main(String[] args){for(String key:PrivacyPolicy.WEATHER_CACHE)check(!PrivacyPolicy.keepSetting("atmosphere",key));for(String key:new String[]{"enabled","weather","time"})check(PrivacyPolicy.keepSetting("atmosphere",key));check(!PrivacyPolicy.keepSetting("atmosphere","futureLocation"));check(!PrivacyPolicy.keepSetting("listen","allowNetworkVoice"));check(PrivacyPolicy.keepSetting("listen","privacy"));check(PrivacyPolicy.keepSetting("reader","font"));System.out.println("PrivacyPolicyTest: "+checks+" checks passed");}
}
