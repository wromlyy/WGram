package org.telegram.messenger;
public class DialogObject { public static boolean isEncryptedDialog(long id) { return (id & 0x4000000000000000L) != 0; } }
