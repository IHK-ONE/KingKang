package com.kingkang.app.device;

/**
 * Runs one shell command as the Shizuku shell user. The implementation lives in the
 * Shizuku process, so it never gains more authority than `adb shell` already has.
 */
interface IDeviceShell {
    /** Reserved by the Shizuku server: ask the user service to exit. */
    void destroy() = 16777114;

    String exec(String command, int timeoutMs) = 1;
}
