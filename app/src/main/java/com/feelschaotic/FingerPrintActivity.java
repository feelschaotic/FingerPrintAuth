package com.feelschaotic;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.feelschaotic.fingerprintauth.R;
import com.feelschaotic.utils.AppUtils;
import com.feelschaotic.utils.DeviceUtils;
import com.feelschaotic.exception.FingerPrintException;
import com.feelschaotic.utils.FingerprintManagerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class FingerPrintActivity extends Activity {

    public final static String TYPE = "type";
    public final static String CLEAR = "clear";
    public final static String LOGIN = "login";//登陆验证场景
    public final static String SETTING = "setting";
    public final static String LOGIN_SETTING = "login_setting";//登陆后的引导设置

    private String mType;
    private boolean isInAuth = false;
    private boolean mIsSupportFingerprint;

    private String mBeginAuthenticateMethodName;
    private ArrayList<String> methodOrderArrayList;
    private Map<String, String> mi5TipsMappingMap;
    private Map<String, String> exceptionTipsMappingMap;
    private FingerprintManagerUtil mFingerprintManagerUtil;
    private FingerPrintTypeController mFingerPrintTypeController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialize();
    }

    protected void initialize() {
        mType = getIntent().getStringExtra(TYPE);
       
        mFingerprintManagerUtil = new FingerprintManagerUtil(this, () -> beginAuthAnim(), new MyAuthCallbackListener());
        mIsSupportFingerprint = mFingerprintManagerUtil.isSupportFingerprint();
        mFingerPrintTypeController = new FingerPrintTypeController();
        methodOrderArrayList = new ArrayList<>();

        initExceptionTipsMapping();
        initByType();
    }

    private void initExceptionTipsMapping() {
        //普通异常情况提示
        exceptionTipsMappingMap = new HashMap<>();
        exceptionTipsMappingMap.put(SETTING, getString(R.string.fingerprint_no_support_fingerprint_gesture));
        exceptionTipsMappingMap.put(LOGIN_SETTING, getString(R.string.fingerprint_no_support_fingerprint_gesture));
        exceptionTipsMappingMap.put(CLEAR, null);
        exceptionTipsMappingMap.put(LOGIN, getString(R.string.fingerprint_no_support_fingerprint_account));

        //小米5乱回调生命周期的异常情况提示
        mi5TipsMappingMap = new HashMap<>();
        mi5TipsMappingMap.put(SETTING, getString(R.string.tips_mi5_setting_open_close_error));
        mi5TipsMappingMap.put(LOGIN_SETTING, getString(R.string.tips_mi5_login_setting_error));
        mi5TipsMappingMap.put(CLEAR, getString(R.string.tips_mi5_setting_open_close_error));
        mi5TipsMappingMap.put(LOGIN, getString(R.string.tips_mi5_login_auth_error));
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        methodOrderArrayList.clear();
    }

    private void stopFingerprintListen() {
        if (mFingerprintManagerUtil != null) {
            mFingerprintManagerUtil.stopsFingerprintListen();
        }
    }

    private void initByType() {
        switch (mType) {
            case SETTING:
            case LOGIN_SETTING:
                initSetting();
                break;
            case CLEAR:
                initVerify(getString(R.string.fingerprint_is_empty_clear));
                break;
            case LOGIN:
                initVerify(getString(R.string.fingerprint_is_empty_login));
                break;
        }
    }


    private void initSetting() {
        if (!mIsSupportFingerprint) {
            jumpToGesture(mType);
            return;
        }
        beginAuthenticate();
    }


    private void initVerify(String errorContent) {
        if (!mIsSupportFingerprint) {
            logoutAndClearFingerPrint();
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
            if (isAuthSuccess) {
                mFingerPrintTypeController.onAuthenticationSucceeded();
            } else {
                onAuthExceptionOrBeIntercept();
            }
        }

        @Override
        public void onAuthenticationError(int errMsgId, String errString) {
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
        if (CLEAR.equals(mType)) {
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
        }
    }

    private class ClearType implements FingerPrintType {
        @Override
        public void onAuthenticationSucceeded() {
            onAuthSuccess(getString(R.string.fingerprint_close_success));
        }

        @Override
        public void onAuthenticationError(String content) {
        }
    }

    private class LoginSettingType implements FingerPrintType {
        @Override
        public void onAuthenticationSucceeded() {
            onAuthSuccess(getString(R.string.fingerprint_set_success));
        }

        @Override
        public void onAuthenticationError(String content) {
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
        }
    }

    private class FingerPrintTypeController implements FingerPrintType {
        private Map<String, FingerPrintType> typeMappingMap = new HashMap<>();

        public FingerPrintTypeController() {
            typeMappingMap.put(SETTING, new SettingType());
            typeMappingMap.put(LOGIN_SETTING, new LoginSettingType());
            typeMappingMap.put(CLEAR, new ClearType());
            typeMappingMap.put(LOGIN, new LoginAuthType());
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

}
