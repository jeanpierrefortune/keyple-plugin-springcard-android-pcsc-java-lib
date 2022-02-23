/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.util;

import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Helper class to execute runnable in background. Thread Useful to call AsyncTask from background
 * (or unknown) thread.
 *
 * <p>
 */
public class BackgroundThreadExecutor {

  private static boolean isUiThread() {
    return Thread.currentThread() == Looper.getMainLooper().getThread();
  }

  public BackgroundThreadExecutor(@NonNull Runnable command) {
    if (!isUiThread()) {
      command.run();
    } else {
      new Thread(command).start();
    }
  }

  public BackgroundThreadExecutor(@NonNull final Runnable command, final int latency) {
    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                if (!isUiThread()) {
                  command.run();
                } else {
                  new Thread(command).start();
                }
              }
            },
            latency);
  }
}
