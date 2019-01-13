package com.feelschaotic.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
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

    private static final String KEY_STORE_NAME = "AndroidKeyStore";
    /**
     * 保证唯一，建议使用域名区别
     */
    private static final String KEY_NAME = "fingerprint_key";
    private Cipher mCipher;
    private KeyStore mKeyStore;
    private KeyGenerator mKeyGenerator;
    private ICryptoObjectCreateListener mCreateListener;
    private FingerprintManagerCompat.CryptoObject mCryptoObject;

    public CryptoObjectCreatorHelper(ICryptoObjectCreateListener createListener) {
        mCreateListener = createListener;
        init();
    }

    private void init() {
        prepareData(true);
        callbackResult();
    }

    private void callbackResult() {
        if (mCipher != null) {
            mCryptoObject = new FingerprintManagerCompat.CryptoObject(mCipher);
        }
        if (mCreateListener != null) {
            mCreateListener.onDataPrepared(mCryptoObject);
        }
    }

    private void prepareData(boolean retry) {
        try {
            mKeyStore = providesKeystore();
            mKeyGenerator = providesKeyGenerator();
            mCipher = providesCipher();

            createKey();
            initCipher();
        } catch (Exception e) {
            onPrepareDataError(retry);
        }
    }

    private KeyStore providesKeystore() throws KeyStoreException {
        return KeyStore.getInstance(KEY_STORE_NAME);
    }

    private KeyGenerator providesKeyGenerator() throws NoSuchProviderException, NoSuchAlgorithmException {
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_NAME);
    }

    private Cipher providesCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     */
    private void createKey() throws InvalidAlgorithmParameterException, CertificateException, NoSuchAlgorithmException, IOException {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.
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
    }

    /**
     * Initialize the {@link Cipher} instance with the created key in the {@link #createKey()}
     * method.
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    private void initCipher() throws CertificateException, NoSuchAlgorithmException, IOException, InvalidKeyException, UnrecoverableKeyException, KeyStoreException {
        mKeyStore.load(null);
        SecretKey key = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
        mCipher.init(Cipher.ENCRYPT_MODE, key);
    }

    private void onPrepareDataError(boolean retry) {
        if (retry) {
            prepareData(false);
        }
    }

    public void onDestroy() {
        mCipher = null;
        mCryptoObject = null;
        mKeyStore = null;
    }

}
