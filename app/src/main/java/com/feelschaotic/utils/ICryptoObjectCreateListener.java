package com.feelschaotic.utils;

import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;

public interface ICryptoObjectCreateListener {
    void onDataPrepared(FingerprintManagerCompat.CryptoObject cryptoObject);
}