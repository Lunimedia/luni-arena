# The native bridge exports JNI names based on this exact Java class and method names.
# Other application classes may be optimized and renamed by R8.
-keep class br.com.knightjoga.Core { *; }
