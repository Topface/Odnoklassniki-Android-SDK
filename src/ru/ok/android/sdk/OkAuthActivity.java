package ru.ok.android.sdk;

import static ru.ok.android.sdk.Shared.API_URL_BASE;
import static ru.ok.android.sdk.Shared.GRANT_AUTH_CODE;
import static ru.ok.android.sdk.Shared.METHOD_AUTHORIZE;
import static ru.ok.android.sdk.Shared.OAUTH_GET_CODE_URL;
import static ru.ok.android.sdk.Shared.OAUTH_GET_TOKEN_PATH;
import static ru.ok.android.sdk.Shared.OAUTH_RDRCT_AUTH_PREFIX;
import static ru.ok.android.sdk.Shared.OAUTH_RDRCT_FULL_PREFIX;
import static ru.ok.android.sdk.Shared.OAUTH_RDRCT_SCHEME;
import static ru.ok.android.sdk.Shared.OAUTH_RESPONSE_TYPE;
import static ru.ok.android.sdk.Shared.PARAM_ACCESS_TOKEN;
import static ru.ok.android.sdk.Shared.PARAM_APP_KEY;
import static ru.ok.android.sdk.Shared.PARAM_CANCELLED;
import static ru.ok.android.sdk.Shared.PARAM_CLIENT_ID;
import static ru.ok.android.sdk.Shared.PARAM_CLIENT_SECRET;
import static ru.ok.android.sdk.Shared.PARAM_CODE;
import static ru.ok.android.sdk.Shared.PARAM_GRANT_TYPE;
import static ru.ok.android.sdk.Shared.PARAM_METHOD;
import static ru.ok.android.sdk.Shared.PARAM_OAUTH_ONLY;
import static ru.ok.android.sdk.Shared.PARAM_REDIRECT_URI;
import static ru.ok.android.sdk.Shared.PARAM_REFRESH_TOKEN;
import static ru.ok.android.sdk.Shared.PARAM_SCOPES;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Random;

import org.apache.http.impl.client.DefaultHttpClient;

import ru.ok.android.sdk.util.OkNetUtil;
import ru.ok.android.sdk.util.OkThreadUtil;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

public class OkAuthActivity extends Activity {
	
	private static final int SSO_ACTIVITY_REQUEST_CODE = 31337;
	
	private static final String EXTRA_AUTH_STATE = "authstate";
	
	// Default state
	private static final int STATE_INIT = 0;
	// Intent for browser was sent
	private static final int STATE_SENT_BROWSER = 1;
	// Activity starts to wait for browser response
	private static final int STATE_WAIT_BROWSER = 2;
	// Activity received response from a browser
	private static final int STATE_RETURN_BROWSER = 3;
	// SSO authorization was requested
	private static final int STATE_SENT_SSO = 4;
	// Activity is done with authorization and is finishing
	private static final int STATE_FINISH = 5;
	
	private String mAppId;
	private String mAppKey;
	private String mAppSecret;
	private String[] mScopes;
	
	private Random mRandom = new Random();
	
	private int mAuthState = STATE_INIT;
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mAuthState = savedInstanceState.getInt(EXTRA_AUTH_STATE);
			mAppId = savedInstanceState.getString(PARAM_CLIENT_ID);
			mAppSecret = savedInstanceState.getString(PARAM_CLIENT_SECRET);
			mAppKey = savedInstanceState.getString(PARAM_APP_KEY);
			mScopes = savedInstanceState.getStringArray(PARAM_SCOPES);
		}
		doActionOnIntent(getIntent());
	}
	
	@Override
	protected void onNewIntent(final Intent intent) {
		// New intent received. Most likely a response from a browser auth
		super.onNewIntent(intent);
		doActionOnIntent(intent);
	}
	
	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(EXTRA_AUTH_STATE, mAuthState);
		outState.putString(Shared.PARAM_CLIENT_ID, mAppId);
		outState.putString(Shared.PARAM_APP_KEY, mAppKey);
		outState.putString(Shared.PARAM_CLIENT_SECRET, mAppSecret);
		outState.putStringArray(Shared.PARAM_SCOPES, mScopes);
	}
	
	private final void doActionOnIntent(final Intent intent) {
		final int method = intent.getIntExtra(PARAM_METHOD, 0);
		intent.removeExtra(PARAM_METHOD);
		if (method == METHOD_AUTHORIZE) {
			// Authorization requested
			onAuthAction(intent);
		} else {
			final Uri uri = intent.getData();
			if (uri != null) {
				if (hasAppInfo()) {
					final String scheme = uri.getScheme();
					final String authority = uri.getAuthority();
					if (scheme.equals(OAUTH_RDRCT_SCHEME) && authority.equals(OAUTH_RDRCT_AUTH_PREFIX + mAppId)) {
						mAuthState = STATE_RETURN_BROWSER;
						doBrowserAction(uri);
					} else {
						onFail(false);
					}
				} else {
					// User pressed "login" in browser, but authorization process was already finished before, so do nothing
					finish();
					overridePendingTransition(0, 0);
				}
			}
		}
	}
	
	private final void onAuthAction(final Intent intent) {
		boolean failed = true;
		final Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppId = extras.getString(PARAM_CLIENT_ID);
			mAppSecret = extras.getString(PARAM_CLIENT_SECRET);
			mAppKey = extras.getString(PARAM_APP_KEY);
			mScopes = extras.getStringArray(PARAM_SCOPES);
			if (hasAppInfo()) {
				failed = false;
				boolean oauth = extras.getBoolean(PARAM_OAUTH_ONLY);
				if (!oauth) {
					// Try authorization through Odnoklassniki application
					oauth = !trySsoAuthorization();
				}
				if (oauth) {
					// If there is no application to authorize us - try browser
					startOauthAuthorization();
					mAuthState = STATE_SENT_BROWSER;
				} else {
					mAuthState = STATE_SENT_SSO;
				}
			}
		}
		if (failed) {
			// If missing any extras required for auth
			onFail(false);
		}
	}
	
	private final void doBrowserAction(final Uri uri) {
		final String code = uri.getQueryParameter("code");
		if (code == null) {
			final String error = uri.getQueryParameter("error");
			onFail("access_denied".equals(error));
		} else {
			new Thread() {
				@Override
				public void run() {
					final String response = getTokenByCode(code);
					final String[] parsedResponse = Util.parseTokenResponse(response, false);
					if ((parsedResponse == null) || (parsedResponse[0] == null)) {
						onFail(false);
					} else {
						final String accessToken = parsedResponse[0];
						final String refreshToken = parsedResponse[1];
						OkThreadUtil.executeOnMain(new Runnable() {
							public void run() {
								onSuccess(accessToken, refreshToken);
							}
						});
					}
				}
			}.start();
		}
	}
	
	protected final String getTokenByCode(final String code) {
		final String url = API_URL_BASE + OAUTH_GET_TOKEN_PATH;
		final HashMap<String, String> params = new HashMap<String, String>(5);
		params.put(PARAM_CODE, code);
		params.put(PARAM_REDIRECT_URI, OAUTH_RDRCT_FULL_PREFIX + mAppId);
		params.put(PARAM_GRANT_TYPE, GRANT_AUTH_CODE);
		params.put(PARAM_CLIENT_ID, mAppId);
		params.put(PARAM_CLIENT_SECRET, mAppSecret);
		String response = null;
		try {
			response = OkNetUtil.performPostRequest(new DefaultHttpClient(), url, params);
		} catch (IOException exc) {}
		return response;
	}
	
	/* OAUTH AUTHORIZATION */
	private final void startOauthAuthorization() {
		final String oauthUrl = buildOAuthUrl();
		final Intent intent = new Intent();
		intent.setAction(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(oauthUrl));
		startActivity(intent);
	}
	
	@SuppressWarnings("deprecation")
	private final String buildOAuthUrl() {
		final String schemaString = URLEncoder.encode(OAUTH_RDRCT_FULL_PREFIX + mAppId);
		String url = String.format(OAUTH_GET_CODE_URL, mAppId, OAUTH_RESPONSE_TYPE, schemaString, mRandom.nextInt(9999));
		if ((mScopes != null) && (mScopes.length > 0)) {
			final String scopesString = URLEncoder.encode(TextUtils.join(";", mScopes));
			url = url + "&scope=" + scopesString;
		}
		return url;
	}
	
	/* SSO AUTHORIZATION */
	private final boolean trySsoAuthorization() {
		boolean ssoAvailable = false;
        final Intent intent = new Intent();
        intent.setClassName("ru.ok.android", "ru.ok.android.external.LoginExternal");
        final ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, 0);
        if (resolveInfo != null) {
            try {
            	final PackageInfo packageInfo = getPackageManager().getPackageInfo(resolveInfo.activityInfo.packageName, PackageManager.GET_SIGNATURES);
            	for (final Signature signature : packageInfo.signatures) {
                    if (signature.toCharsString().equals(ODKL_APP_SIGNATURE)) {
                    	ssoAvailable = true;
                    }
                }
            } catch (NameNotFoundException exc) { }
        	if (ssoAvailable) {
        		intent.putExtra(PARAM_CLIENT_ID, mAppId);
        		intent.putExtra(PARAM_CLIENT_SECRET, mAppSecret);
                if ((mScopes != null) && (mScopes.length > 0)) {
                    intent.putExtra(PARAM_SCOPES, mScopes);
                }
                try {
                	startActivityForResult(intent, SSO_ACTIVITY_REQUEST_CODE);
                } catch (ActivityNotFoundException exc) {
                	ssoAvailable = false;
                }
        	}
        }
        return ssoAvailable;
	}
	
	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == SSO_ACTIVITY_REQUEST_CODE) {
			boolean error = true;
			if (resultCode == RESULT_OK) {
				final String accessToken = data.getStringExtra(PARAM_ACCESS_TOKEN);
				final String refreshToken = data.getStringExtra(PARAM_REFRESH_TOKEN);
				if (accessToken != null) {
					error = false;
					onSuccess(accessToken, refreshToken);
				}
			} else if (resultCode == RESULT_CANCELED) {
				error = false;
				onFail(true);
			}
			if (error) {
				onFail(false);
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);	
		}
		onBackPressed();
	}
	
	protected final void onFail(final boolean cancelled) {
		Bundle bundle = null;
		if (cancelled) {
			bundle = new Bundle();
			bundle.putBoolean(PARAM_CANCELLED, true);
		}
		sendBundle(bundle);
		mAuthState = STATE_FINISH;
		onBackPressed();
	}
	
	protected final void onSuccess(final String accessToken, final String refreshToken) {
		TokenStore.storeTokens(this, accessToken, refreshToken);
		final Bundle bundle = new Bundle();
		bundle.putString(PARAM_ACCESS_TOKEN, accessToken);
		bundle.putString(PARAM_REFRESH_TOKEN, refreshToken);
		sendBundle(bundle);
		mAuthState = STATE_FINISH;
		onBackPressed();
	}
	
	private final void sendBundle(final Bundle bundle) {
		if (Odnoklassniki.hasInstance()) {
			Odnoklassniki.getInstance(this).onTokenResponseRecieved(bundle);
		}
	}
	
	private final boolean hasAppInfo() {
		return (mAppId != null) && (mAppKey != null) && (mAppSecret != null);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (mAuthState == STATE_SENT_BROWSER) {
			mAuthState = STATE_WAIT_BROWSER;
		} else if (mAuthState == STATE_WAIT_BROWSER) {
			onFail(true);
		}
	}
	
	private static final String ODKL_APP_SIGNATURE = "3082025b308201c4a00302010202044f6760f9300d06092a864886f70d01010505003071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f763020170d3132303331393136333831375a180f32303636313232313136333831375a3071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f7630819f300d06092a864886f70d010101050003818d003081890281810080bea15bf578b898805dfd26346b2fbb662889cd6aba3f8e53b5b27c43a984eeec9a5d21f6f11667d987b77653f4a9651e20b94ff10594f76a93a6a36e6a42f4d851847cf1da8d61825ce020b7020cd1bc2eb435b0d416908be9393516ca1976ff736733c1d48ff17cd57f21ad49e05fc99384273efc5546e4e53c5e9f391c430203010001300d06092a864886f70d0101050500038181007d884df69a9748eabbdcfe55f07360433b23606d3b9d4bca03109c3ffb80fccb7809dfcbfd5a466347f1daf036fbbf1521754c2d1d999f9cbc66b884561e8201459aa414677e411e66360c3840ca4727da77f6f042f2c011464e99f34ba7df8b4bceb4fa8231f1d346f4063f7ba0e887918775879e619786728a8078c76647ed";

}
