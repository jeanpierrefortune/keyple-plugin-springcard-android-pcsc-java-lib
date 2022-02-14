/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.pcsclike.example.util;

import android.os.Handler;
import android.os.Looper;

/**
 * Helper class to execute runnable in UI Thread Useful to call AsyncTask from background (or
 * unknown) thread.
 *
 * <p>
 */
public class UiThreadExecutor {
  private static final Handler sUiHandler;
  private static final Thread sUiThread;

  static {
    Looper uiLooper = Looper.getMainLooper();
    sUiThread = uiLooper.getThread();
    sUiHandler = new Handler(uiLooper);
  }

  public UiThreadExecutor(Runnable runnable) {
    if (Thread.currentThread() == sUiThread) {
      runnable.run();
    } else {
      sUiHandler.post(runnable);
    }
  }

  public UiThreadExecutor(Runnable runnable, int latency) {
    sUiHandler.postDelayed(runnable, latency);
  }
}
