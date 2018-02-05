package com.feelschaotic.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * 加密对象包装类CryptoObject的创建类
 * 采用对称加密
 * 主要实现步骤如下：
 * 1. 新建一个KeyStore密钥库，用于存放密钥
 * 2. 获取KeyGenerator密钥生成工具，生成密钥
 * 3. 通过密钥初始化Cipher对象，生成加密对象CryptoObject
 */
@TargetApi(Build.VERSION_CODES.M)
public class CryptoObjectCreatorHelper {

    private static final String KEY_NAME = "fingerprint_key";//保证唯一，建议使用域名区别
    private static final String KEY_STORE_NAME = "AndroidKeyStore";
    private static final String THREAD_NAME = "FingerprintLogic:InitThread";
    private static final String TAG = "CryptoObjectCreatorHelper";
    private FingerprintManagerCompat.CryptoObject mCryptoObject;
    private KeyStore mKeyStore;
    private KeyGenerator mKeyGenerator;
    private Cipher mCipher;
    private ICryptoObjectCreateListener mCreateListener;

    private Handler mHandler = new Handler(msg -> {
        if (msg.what == 1 && mCreateListener != null) {
            mCreateListener.onDataPrepared(mCryptoObject);
        }
        return false;
    });

    public interface ICryptoObjectCreateListener {
        void onDataPrepared(FingerprintManagerCompat.CryptoObject cryptoObject);
    }

    public CryptoObjectCreatorHelper(ICryptoObjectCreateListener createListener) {
        mKeyStore = providesKeystore();
        mKeyGenerator = providesKeyGenerator();
        mCipher = providesCipher();
        mCreateListener = createListener;
        if (mKeyStore != null && mKeyGenerator != null && mCipher != null) {
            mCryptoObject = new FingerprintManagerCompat.CryptoObject(mCipher);
        }
        prepareData();
    }


    private void prepareData() {
        new Thread(THREAD_NAME) {
            @Override
            public void run() {
                try {
                    if (mCryptoObject != null) {
                        createKey();
                        // Set up the crypto object for later. The object will be authenticated by use
                        // of the fingerprint.
                        if (!initCipher()) {
                            Log.d(TAG, "Failed to init Cipher.");
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, " Failed to init Cipher, e:" + Log.getStackTraceString(e));
                }
                mHandler.sendEmptyMessage(1);
            }
        }.start();
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     */
    private void createKey() {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.
        try {
            mKeyStore.load(null);
            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            mKeyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    // Require the user to authenticate with a fingerprint to authorize every use
                    // of the key
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            mKeyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException
                | CertificateException | IOException e) {
            Log.d(TAG, " Failed to createKey, e:" + Log.getStackTraceString(e));
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialize the {@link Cipher} instance with the created key in the {@link #createKey()}
     * method.
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    private boolean initCipher() {
        try {
            mKeyStore.load(null);
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
            mCipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            Log.d(TAG, "KeyPermanentlyInvalidatedException Failed to initCipher, e:" + Log.getStackTraceString(e));
            return false;
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            Log.d(TAG, " Failed to initCipher, e :" + Log.getStackTraceString(e));
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    private KeyStore providesKeystore() {
        try {
            return KeyStore.getInstance(KEY_STORE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    private KeyGenerator providesKeyGenerator() {
        try {
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    private Cipher providesCipher() {
        try {
            return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (Throwable e) {
            return null;
        }
    }

    public void onDestroy() {
        mCipher = null;
        mCryptoObject = null;
        mCipher = null;
        mKeyStore = null;
    }

}
