package com.feelschaotic;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;

import com.feelschaotic.fingerprintauth.R;
import com.feelschaotic.utils.AppUtils;
import com.feelschaotic.utils.DeviceUtils;
import com.feelschaotic.utils.FingerPrintException;
import com.feelschaotic.utils.FingerprintManagerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class FingerPrintActivity extends Activity {

    public final static String GESTURE_FINGER_TYPE = "gesture_finger_type";
    public final static String GESTURE_FINGER_LOGIN = "gesture_finger_login";//登陆验证场景
    public final static String GESTURE_FINGER_SETTING = "gesture_finger_setting";
    public final static String GESTURE_FINGER_LOGIN_SETTING = "gesture_finger_login_setting";//登陆后的引导设置 和设置页的设置区别开来 手势密码无该场景
    public final static String GESTURE_FINGER_CLEAR = "gesture_finger_clear";


    public boolean canBack = false;
    private String mType;
    public static boolean isShow;
    private boolean mIsSupportFingerprint;
    private boolean isInAuth = false;

    private FingerprintManagerUtil mFingerprintManagerUtil;
    private FingerPrintTypeController mFingerPrintTypeController;
    private ArrayList<String> methodOrderArrayList;
    private String mBeginAuthenticateMethodName;
    private Map<String, String> exceptionTipsMappingMap;
    private Map<String, String> mi5TipsMappingMap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialize();
    }

    protected void initialize() {
        mType = getIntent().getStringExtra(GESTURE_FINGER_TYPE);
        if (TextUtils.isEmpty(mType)) {
            mType = GESTURE_FINGER_LOGIN;
        }

        mFingerprintManagerUtil = new FingerprintManagerUtil(this, () -> beginAuthAnim(), new MyAuthCallbackListener());
        mIsSupportFingerprint = mFingerprintManagerUtil.isSupportFingerprint();
        mFingerPrintTypeController = new FingerPrintTypeController();

        methodOrderArrayList = new ArrayList<>();

        //普通异常情况提示
        exceptionTipsMappingMap = new HashMap<>();
        exceptionTipsMappingMap.put(GESTURE_FINGER_SETTING, getString(R.string.fingerprint_no_support_fingerprint_gesture));
        exceptionTipsMappingMap.put(GESTURE_FINGER_LOGIN_SETTING, getString(R.string.fingerprint_no_support_fingerprint_gesture));
        exceptionTipsMappingMap.put(GESTURE_FINGER_CLEAR, null);
        exceptionTipsMappingMap.put(GESTURE_FINGER_LOGIN, getString(R.string.fingerprint_no_support_fingerprint_account));

        //小米5乱回调生命周期的异常情况提示
        mi5TipsMappingMap = new HashMap<>();
        mi5TipsMappingMap.put(GESTURE_FINGER_SETTING, getString(R.string.tips_mi5_setting_open_close_error));
        mi5TipsMappingMap.put(GESTURE_FINGER_LOGIN_SETTING, getString(R.string.tips_mi5_login_setting_error));
        mi5TipsMappingMap.put(GESTURE_FINGER_CLEAR, getString(R.string.tips_mi5_setting_open_close_error));
        mi5TipsMappingMap.put(GESTURE_FINGER_LOGIN, getString(R.string.tips_mi5_login_auth_error));

        initByType();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isShow = true;
        mIsSupportFingerprint = mFingerprintManagerUtil.isSupportFingerprint();
        //回来的时候自动调起验证
        if (isInAuth) {
            initByType();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopFingerprintListen();
        isInAuth = mFingerprintManagerUtil != null && mFingerprintManagerUtil.getIsInAuth();
        methodOrderArrayList.add(AppUtils.getMethodName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isShow = false;
        methodOrderArrayList.clear();
    }

    private void stopFingerprintListen() {
        if (mFingerprintManagerUtil != null) {
            mFingerprintManagerUtil.stopsFingerprintListen();
        }
        stopAnim();
    }

    private void stopAnim() {
    }

    private void initByType() {
        switch (mType) {
            case GESTURE_FINGER_SETTING:
                canBack = true;
                initSettingView();
                initSetting();
                break;
            case GESTURE_FINGER_LOGIN_SETTING:
                initSettingView();
                initSetting();
                break;
            case GESTURE_FINGER_CLEAR:
                canBack = true;
                initVerifyView();
                initVerify(getString(R.string.fingerprint_is_empty_clear));
                break;
            case GESTURE_FINGER_LOGIN:
                initVerifyView();
                initVerify(getString(R.string.fingerprint_is_empty_login));
                break;
        }
    }

    private void initSettingView() {
    }

    private void initSetting() {
        if (!mIsSupportFingerprint) {
            jumpToGesture(mType);
            return;
        }
        beginAuthenticate();
    }

    private void initVerifyView() {
    }

    private void initVerify(String errorContent) {
        if (!mIsSupportFingerprint) {

            logoutAndClearFingerPrint();
            //showDialog And jumpToInputPhone
            return;
        }

        beginAuthenticate();
    }

    private void beginAuthenticate() {
        mBeginAuthenticateMethodName = AppUtils.getMethodName();
        methodOrderArrayList.add(mBeginAuthenticateMethodName);
        try {
            mFingerprintManagerUtil.beginAuthenticate();
        } catch (FingerPrintException e) {
            onAuthExceptionOrBeIntercept();
        }
    }

    private void beginAuthAnim() {
    }

    public class MyAuthCallbackListener implements FingerprintManagerUtil.AuthenticationCallbackListener {

        @Override
        public void onAuthenticationSucceeded(boolean isAuthSuccess) {
            methodOrderArrayList.add(AppUtils.getMethodName());
            stopAnim();
            if (isAuthSuccess) {
                mFingerPrintTypeController.onAuthenticationSucceeded();
            } else {
                onAuthExceptionOrBeIntercept();
            }
        }

        @Override
        public void onAuthenticationError(int errMsgId, String errString) {
            stopAnim();
            switch (errMsgId) {
                case FingerprintManagerUtil.MyAuthCallback.ERROR_BEYOND:
                    mFingerPrintTypeController.onAuthenticationError(null);
                    break;
                case FingerprintManagerUtil.MyAuthCallback.ERROR_CANCEL:
                    compatibilityDispose();
                    methodOrderArrayList.clear();
                    break;
                default:
                    break;
            }
        }

        /**
         * 针对小米5的兼容，小米5在验证过程中切到后台再回来时，开启验证会直接回调onAuthenticationError，无法继续验证
         * 所以存储函数调用顺序，判断是否一开启验证马上就回调onAuthenticationError
         */
        private void compatibilityDispose() {
            int size = methodOrderArrayList.size();
            if (size <= 0) {
                return;
            }
            if ("MI 5".equals(DeviceUtils.getPhoneModel()) && mBeginAuthenticateMethodName.equals(methodOrderArrayList.get(size - 1))) {
                mFingerPrintTypeController.onAuthenticationError(mi5TipsMappingMap.get(mType));
            }
        }

        @Override
        public void onAuthenticationFailed() {
            methodOrderArrayList.add(AppUtils.getMethodName());
            onAuthFail(getString(R.string.fingerprint_auth_fail));
        }

        @Override
        public void onAuthenticationHelp(String helpString) {
            methodOrderArrayList.add(AppUtils.getMethodName());
            onAuthFail(helpString);
        }
    }

    /**
     * 验证过程异常 或 验证结果被恶意劫持
     * 该失败场景都会清掉指纹再次登陆引导设置，所以如果是关闭场景按成功来处理
     */
    private void onAuthExceptionOrBeIntercept() {
        if (GESTURE_FINGER_CLEAR.equals(mType)) {
            mFingerPrintTypeController.onAuthenticationSucceeded();
        } else {
            mFingerPrintTypeController.onAuthenticationError(exceptionTipsMappingMap.get(mType));
            clearFingerPrintSign();
        }
    }

    private void onAuthFail(String text) {
        Toast.makeText(FingerPrintActivity.this, text, Toast.LENGTH_LONG).show();

    }

    private void onAuthSuccess(String text) {
        Toast.makeText(FingerPrintActivity.this, text, Toast.LENGTH_LONG).show();
    }


    private void jumpToGesture(String type) {
    }


    private void logoutAndClearFingerPrint() {
    }

    private void clearFingerPrintSign() {
    }

    private interface FingerPrintType {
        void onAuthenticationSucceeded();

        void onAuthenticationError(String content);
    }

    private class LoginAuthType implements FingerPrintType {
        @Override
        public void onAuthenticationSucceeded() {
            onAuthSuccess(getString(R.string.fingerprint_auth_success));
        }

        @Override
        public void onAuthenticationError(String content) {
            String tempContent = !TextUtils.isEmpty(content) ? content : getString(R.string.fingerprint_auth_error_limit);
            //showDialog And jumpToInputPhone
        }
    }

    private class ClearType implements FingerPrintType {
        @Override
        public void onAuthenticationSucceeded() {
            onAuthSuccess(getString(R.string.fingerprint_close_success));
        }

        @Override
        public void onAuthenticationError(String content) {
            String tempContent = !TextUtils.isEmpty(content) ? content : getString(R.string.fingerprint_setting_error_limit);
            //showDialog
        }
    }

    private class LoginSettingType implements FingerPrintType {
        @Override
        public void onAuthenticationSucceeded() {
            onAuthSuccess(getString(R.string.fingerprint_set_success));
        }

        @Override
        public void onAuthenticationError(String content) {
            String tempContent = !TextUtils.isEmpty(content) ? content : getString(R.string.fingerprint_login_setting_error_limit);
            //showDialog And jumpToGesture
        }
    }

    private class SettingType implements FingerPrintType {
        @Override
        public void onAuthenticationSucceeded() {
            onAuthSuccess(getString(R.string.fingerprint_set_success));
            finish();
        }

        @Override
        public void onAuthenticationError(String content) {
            String tempContent = !TextUtils.isEmpty(content) ? content : getString(R.string.fingerprint_setting_error_limit);
            //showDialog
        }
    }

    private class FingerPrintTypeController implements FingerPrintType {
        private Map<String, FingerPrintType> typeMappingMap = new HashMap<>();

        public FingerPrintTypeController() {
            typeMappingMap.put(GESTURE_FINGER_SETTING, new SettingType());
            typeMappingMap.put(GESTURE_FINGER_LOGIN_SETTING, new LoginSettingType());
            typeMappingMap.put(GESTURE_FINGER_CLEAR, new ClearType());
            typeMappingMap.put(GESTURE_FINGER_LOGIN, new LoginAuthType());
        }

        @Override
        public void onAuthenticationSucceeded() {
            FingerPrintType fingerPrintType = typeMappingMap.get(mType);
            if (null != fingerPrintType) {
                fingerPrintType.onAuthenticationSucceeded();
            }
        }

        @Override
        public void onAuthenticationError(String content) {
            FingerPrintType fingerPrintType = typeMappingMap.get(mType);
            if (null != fingerPrintType) {
                fingerPrintType.onAuthenticationError(content);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (canBack) {
            super.onBackPressed();
        }
    }

}
