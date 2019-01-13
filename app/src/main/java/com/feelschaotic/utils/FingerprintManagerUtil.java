package com.feelschaotic.utils;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.os.CancellationSignal;
import android.util.Log;

import com.feelschaotic.exception.FingerPrintException;

import javax.annotation.Nonnull;
import javax.crypto.IllegalBlockSizeException;


public class FingerprintManagerUtil {
    private FingerprintManagerCompat mFingerprintManagerCompat;
    private Context mContext;
    private CancellationSignal cancellationSignal;
    private CryptoObjectCreatorHelper mCryptoObjectCreatorHelper;
    private OnCryptoObjectCreateCompleteListener mListener;
    private AuthenticationCallbackListener mCustomCallback;
    private MyAuthCallback mMyAuthCallback;
    private boolean isInAuth = false;
    private static final String TAG = "FingerprintManagerUtil";
    private int happenCount = 0;

    public FingerprintManagerUtil(Context context, OnCryptoObjectCreateCompleteListener listener, @Nonnull AuthenticationCallbackListener customCallback) {
        this.mContext = context;
        mListener = listener;
        mCustomCallback = customCallback;
        //使用getApplicationContext() 避免内存泄露
        mFingerprintManagerCompat = FingerprintManagerCompat.from(context.getApplicationContext());
        mMyAuthCallback = new MyAuthCallback();
    }

    public boolean isSupportFingerprint() {
        boolean isSupport;
        try {
            isSupport = mFingerprintManagerCompat.isHardwareDetected()//硬件不支持
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M//版本不支持
                    && ((KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardSecure()//没有屏幕锁
                    && mFingerprintManagerCompat.hasEnrolledFingerprints();//系统不存在指纹列表
        } catch (Exception e) {
            //防止有些机型没有以上api会抛空指针异常
            Log.e(TAG, e.getLocalizedMessage());
            return false;
        }
        return isSupport;
    }

    public void beginAuthenticate() throws FingerPrintException {
        try {
            mCryptoObjectCreatorHelper = new CryptoObjectCreatorHelper(this::onCryptoObjectInitialized);
        } catch (Exception e) {
            throw new FingerPrintException(e);
        }
    }

    private void onCryptoObjectInitialized(FingerprintManagerCompat.CryptoObject cryptoObject) {
        Log.d(TAG, "初始化加密对象完成，开始扫描");
        isInAuth = true;
        cancellationSignal = new CancellationSignal();

        mFingerprintManagerCompat.authenticate(cryptoObject, 0, cancellationSignal, mMyAuthCallback, null);
        if (mListener != null) {
            mListener.onCryptoObjectCreateComplete();
        }
    }

    public void stopsFingerprintListen() {
        //如果不取消的话，那么指纹扫描器会一直扫描直到超时（一般为30s，取决于具体的厂商实现），这样的话就会比较耗电。
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
        if (mCryptoObjectCreatorHelper != null) {
            mCryptoObjectCreatorHelper.onDestroy();
        }
    }

    public boolean getIsInAuth() {
        return isInAuth;
    }

    public class MyAuthCallback extends FingerprintManagerCompat.AuthenticationCallback {
        public static final int ERROR_CANCEL = 5;
        public static final int ERROR_BEYOND = 7;

        @Override
        public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
            /**
             * doFinal方法会检查结果是不是会拦截或者篡改过，
             * 如果是的话会抛出一个异常，异常的时候都将认证当做是失败来处理
             */
            super.onAuthenticationSucceeded(result);
            isInAuth = false;
            try {
                result.getCryptoObject().getCipher().doFinal();
                mCustomCallback.onAuthenticationSucceeded(true);
            } catch (IllegalBlockSizeException e) {
                //如果是新录入的指纹，会抛出该异常，需要重新生成密钥对重新验证，这里加个次数限制，避免进入验证异常->重新验证->又验证异常的死循环
                if (happenCount == 0) {
                    beginAuthenticate();
                    happenCount++;
                    return;
                }
                mCustomCallback.onAuthenticationSucceeded(false);
            } catch (Exception e) {
                mCustomCallback.onAuthenticationSucceeded(false);
            }

            mCryptoObjectCreatorHelper.onDestroy();
        }


        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            //验证过程中遇到不可恢复的错误
            super.onAuthenticationError(errMsgId, errString);
            isInAuth = false;
            mCustomCallback.onAuthenticationError(errMsgId, errString.toString());
            mCryptoObjectCreatorHelper.onDestroy();
        }

        @Override
        public void onAuthenticationFailed() {
            super.onAuthenticationFailed();
            Log.d(TAG, "onAuthenticationFailed");
            isInAuth = true;
            mCustomCallback.onAuthenticationFailed();
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            //验证过程中遇到可恢复错误
            super.onAuthenticationHelp(helpMsgId, helpString);
            Log.d(TAG, "onAuthenticationHelp helpString:" + helpString);
            isInAuth = true;
            mCustomCallback.onAuthenticationHelp(helpString.toString());
        }
    }

    public interface OnCryptoObjectCreateCompleteListener {
        void onCryptoObjectCreateComplete();
    }

    public interface AuthenticationCallbackListener {
        void onAuthenticationHelp(String helpString);

        void onAuthenticationFailed();

        void onAuthenticationError(int errMsgId, String errString);

        void onAuthenticationSucceeded(boolean isAuthSuccess);
    }
}
