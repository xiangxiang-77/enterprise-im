# PJSUA2 registers SWIG director callbacks from native code during class init.
# R8 cannot see those JNI lookups and will remove the methods without this.
-keep class org.pjsip.** { *; }
-keep class org.pjsip.pjsua2.** { *; }
