package com.birbit.android.jobqueue;

import java.util.Map;

public interface MapCallback<K, V> {
  void onResult(Map<K, V> result);
  interface MessageWithCallback<K, V> {
    void setCallback(MapCallback<K, V> mapCallback);
  }
}
