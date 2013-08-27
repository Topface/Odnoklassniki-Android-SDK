package ru.ok.android.sdk;

import static ru.ok.android.sdk.Shared.METHOD_AUTHORIZE;
import static ru.ok.android.sdk.Shared.API_REQ_URL_PATH;
import static ru.ok.android.sdk.Shared.API_URL_BASE;
import static ru.ok.android.sdk.Shared.GRANT_REFRESH_TOKEN;
import static ru.ok.android.sdk.Shared.OAUTH_GET_TOKEN_PATH;
import static ru.ok.android.sdk.Shared.PARAM_ACCESS_TOKEN;
import static ru.ok.android.sdk.Shared.PARAM_APP_KEY;
import static ru.ok.android.sdk.Shared.PARAM_CANCELLED;
import static ru.ok.android.sdk.Shared.PARAM_CLIENT_ID;
import static ru.ok.android.sdk.Shared.PARAM_CLIENT_SECRET;
import static ru.ok.android.sdk.Shared.PARAM_GRANT_TYPE;
import static ru.ok.android.sdk.Shared.PARAM_METHOD;
import static ru.ok.android.sdk.Shared.PARAM_OAUTH_ONLY;
import static ru.ok.android.sdk.Shared.PARAM_REFRESH_TOKEN;
import static ru.ok.android.sdk.Shared.PARAM_SCOPES;
import static ru.ok.android.sdk.Shared.PARAM_SIGN;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import ru.ok.android.sdk.util.OkEncryptUtil;
import ru.ok.android.sdk.util.OkNetUtil;
import ru.ok.android.sdk.util.OkScope;
import ru.ok.android.sdk.util.OkThreadUtil;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

public class Odnoklassniki {

	private static Odnoklassniki sOdnoklassniki;

	/**
	 * This method is required to be called before {@link Odnoklassniki#getInstance(Context)}<br>
	 * Note that instance is only created once. Multiple calls to this method wont' create multiple instances of the object
	 * 
	 */
	public static final Odnoklassniki createInstance(final Context context, final String appId, final String appSecret, final String appKey) {
		if ((appId == null) || (appKey == null) || (appSecret == null)) {
			throw new IllegalArgumentException("Missing some application info. All parameters are required");
		}
		if (sOdnoklassniki == null) {
			sOdnoklassniki = new Odnoklassniki(context, appId, appSecret, appKey);
		}
		return sOdnoklassniki;
	}

	/**
	 * Get previously created instance.<br>
	 * You must always call {@link Odnoklassniki#createInstance(Context, String, String, String)} before calling this method, or {@link IllegalStateException} will be thrown
	 */
	public static final Odnoklassniki getInstance(final Context context) {
		if (sOdnoklassniki == null) {
			throw new IllegalStateException("No instance available. Odnoklassniki.createInstance() needs to be called before Odnoklassniki.getInstance()");
		}
		return sOdnoklassniki;
	}
	
	public static final boolean hasInstance() {
		return (sOdnoklassniki != null);
	}

	private Odnoklassniki(final Context context, final String appId, final String appSecret, final String appKey) {
		// APP INFO
		this.mAppId = appId;
		this.mAppSecret = appSecret;
		this.mAppKey = appKey;

		// HTTPCLIENT
		final HttpParams params = new BasicHttpParams();
		final SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		final ClientConnectionManager cm = new ThreadSafeClientConnManager(params, registry);
		mHttpClient = new DefaultHttpClient(cm, params);

		// RESTORE
		mAccessToken = TokenStore.getStoredAccessToken(context);
		mRefreshToken = TokenStore.getStoredRefreshToken(context);
	}

	// Application info
	protected String mAppId;
	protected String mAppSecret;
	protected String mAppKey;

	// Current tokens
	protected String mAccessToken;
	protected String mRefreshToken;

	// Listeners
	protected OkTokenRequestListener mTokenRequestListener;

	// Stuff
	protected HttpClient mHttpClient;

	/* *** AUTHORIZATION *** */

	/**
	 * If user has Odnoklassniki application installed, SDK will try to authorize user through it, otherwise, for safety reasons, authorization through browser will be requested.
	 * 
	 * @param context
	 * @param tokenRequestListener
	 */
	public final void requestAuthorization(final Context context) {
		requestAuthorization(context, false, (String) null);
	}

	/**
	 * If user has Odnoklassniki application installed, SDK will try to authorize user through it, otherwise, for safety reasons, authorization through browser will be requested.<br>
	 * With oauthOnly flag set to true, the authorization will be requested only through browser.
	 * 
	 * @param activity
	 * @param tokenRequestListener
	 * @param oauthOnly
	 */
	public final void requestAuthorization(final Context context, final OkTokenRequestListener tokenRequestListener, final boolean oauthOnly) {
		requestAuthorization(context, oauthOnly, (String) null);
	}

	/**
	 * If user has Odnoklassniki application installed, SDK will try to authorize user through it, otherwise, for safety reasons, authorization through browser will be requested.<br>
	 * With oauthOnly flag set to true, the authorization will be requested only through browser.
	 * 
	 * @param context
	 * @param tokenRequestListener
	 * @param scopes
	 *            {@link OkScope} - application request permissions as per {@link OkScope}.
	 */
	public final void requestAuthorization(final Context context, final boolean oauthOnly, final String... scopes) {
		final Intent authIntent = new Intent(context, OkAuthActivity.class);
		authIntent.putExtra(PARAM_METHOD, METHOD_AUTHORIZE);
		authIntent.putExtra(PARAM_CLIENT_ID, mAppId);
		authIntent.putExtra(PARAM_APP_KEY, mAppKey);
		authIntent.putExtra(PARAM_CLIENT_SECRET, mAppSecret);
		authIntent.putExtra(PARAM_OAUTH_ONLY, oauthOnly);
		authIntent.putExtra(PARAM_SCOPES, scopes);
		context.startActivity(authIntent);
	}

	/**
	 * Try and refresh access token.
	 * <p>
	 * <b>Note that refresh token is only valid during 30 days after authentication. After that period the user must re-authenticate.</b>
	 * 
	 * @param context
	 * @param tokenRequestListener
	 * @throws IllegalArgumentException
	 *             will be thrown if refresh token is not available and user should re-authenticate.
	 */
	public final void refreshToken(final Context context) throws IllegalArgumentException {
		if (mRefreshToken == null) {
			throw new IllegalArgumentException("Refresh token is unavailable. Please reauthorize");
		}
		new Thread() {
			@Override
			public void run() {
				final String url = API_URL_BASE + OAUTH_GET_TOKEN_PATH;
				final HashMap<String, String> params = new HashMap<String, String>(4);
				params.put(PARAM_REFRESH_TOKEN, mRefreshToken);
				params.put(PARAM_GRANT_TYPE, GRANT_REFRESH_TOKEN);
				params.put(PARAM_CLIENT_ID, mAppId);
				params.put(PARAM_CLIENT_SECRET, mAppSecret);
				String newAccessToken = null;
				String response = null;
				try {
					response = OkNetUtil.performPostRequest(mHttpClient, url, params);
				} catch (IOException exc) {
				}
				if (response != null) {
					final String[] parsedResponse = Util.parseTokenResponse(response, true);
					if (parsedResponse != null) {
						newAccessToken = parsedResponse[0];
					}
				}
				if (newAccessToken == null) {
					notifyFailed(false);
				} else {
					mAccessToken = newAccessToken;
					TokenStore.storeAccessToken(context, newAccessToken);
					notifyTokenSuccess(newAccessToken);
				}
			}
		}.start();

	}

	void onTokenResponseRecieved(final Bundle result) {
		if (result == null) {
			notifyFailed(false);
		} else {
			final boolean cancelled = result.getBoolean(PARAM_CANCELLED);
			if (cancelled) {
				notifyFailed(true);
			} else {
				final String accessToken = result.getString(PARAM_ACCESS_TOKEN);
				final String refreshToken = result.getString(PARAM_REFRESH_TOKEN);
				if (accessToken == null) {
					notifyFailed(false);
				} else {
					mAccessToken = accessToken;
					mRefreshToken = refreshToken;
					notifyTokenSuccess(accessToken);
				}
			}
		}
	}

	protected final void notifyFailed(final boolean cancelled) {
		if (mTokenRequestListener != null) {
			OkThreadUtil.executeOnMain(new Runnable() {
				public void run() {
					if (cancelled) {
						mTokenRequestListener.onCancel();
					} else {
						mTokenRequestListener.onError();
					}
				}
			});
		}
	}

	protected final void notifyTokenSuccess(final String accessToken) {
		if (mTokenRequestListener != null) {
			OkThreadUtil.executeOnMain(new Runnable() {
				public void run() {
					mTokenRequestListener.onSuccess(accessToken);
				}
			});
		}
	}

	/* **** API REQUESTS *** */

	/**
	 * Call an API method and get the result as a String.
	 * <p>
	 * <b>Note that those calls MUST be performed in a non-UI thread.</b>
	 * 
	 * @param apiMethod
	 *            - odnoklassniki api method.
	 * @param httpMethod
	 *            - only "get" and "post" are supported.
	 * @return
	 * 
	 * @throws IOException
	 *             in case of a problem or the connection was aborted.
	 */
	public final String request(final String apiMethod, final String httpMethod) throws IOException {
		return request(apiMethod, null, httpMethod);
	}

	/**
	 * Call an API method and get the result as a String.
	 * <p>
	 * <b>Note that those calls MUST be performed in a non-UI thread.</b>
	 * 
	 * @param apiMethod
	 *            - odnoklassniki api method.
	 * @param params
	 * @param httpMethod
	 *            - only "get" and "post" are supported.
	 * @return
	 * @throws IOException
	 */
	public final String request(final String apiMethod, final Map<String, String> params, final String httpMethod) throws IOException {
		if (apiMethod == null) {
			throw new IllegalArgumentException("Api method can't be null");
		}
		Map<String, String> requestparams = new TreeMap<String, String>();
		if ((params != null) && !params.isEmpty()) {
			requestparams.putAll(params);
		}
		requestparams.put(PARAM_APP_KEY, mAppKey);
		requestparams.put(PARAM_METHOD, apiMethod);
		signParameters(requestparams);
		requestparams.put(PARAM_ACCESS_TOKEN, mAccessToken);
		final String requestUrl = API_URL_BASE + API_REQ_URL_PATH;
		String response = null;
		if ("post".equalsIgnoreCase(httpMethod)) {
			response = OkNetUtil.performPostRequest(mHttpClient, requestUrl, requestparams);
		} else {
			response = OkNetUtil.performGetRequest(mHttpClient, requestUrl, requestparams);
		}
		return response;
	}

	/**
	 * Convenience method to send invitation to the application to friends.
	 * <p>
	 * <b>Important: User must confirm the list of recipients. It must be obvious for user, that his action will result sending the pack of invitations to other users. Violating this rule will cause the application to be blocked by administration. In
	 * case of any questions or doubts please contact API support team.</b>
	 * <p>
	 * <b>Note: Use method friends.getByDevices to get user's friends having devices you are interested in.</b>
	 * 
	 * @param friendUids
	 *            - list of recipient friend ids (required).
	 * @param invitationText
	 *            - invitation text (can be null).
	 * @param deviceGroups
	 *            - list of device groups on which the invitation will be shown. Check {@link Devices} enum for the list of supported device groups (can be null).
	 * @return
	 * @throws IOException
	 */
	public final String inviteFriends(final Collection<String> friendUids, final String invitationText, final String... deviceGroups) throws IOException {
		if ((friendUids == null) || friendUids.isEmpty()) {
			throw new IllegalArgumentException("You must provide at least one valid friend UID");
		}
		final String friendsParamValue = TextUtils.join(";", friendUids);
		final Map<String, String> params = new HashMap<String, String>();
		params.put("uids", friendsParamValue);
		if (!TextUtils.isEmpty(invitationText)) {
			params.put("text", invitationText);
		}
		if ((deviceGroups != null) && (deviceGroups.length > 0)) {
			final String deviceParamValue = TextUtils.join(";", deviceGroups);
			params.put("devices", deviceParamValue);
		}
		return request("friends.appInvite", params, "get");
	}

	private final void signParameters(final Map<String, String> params) {
		final StringBuilder sb = new StringBuilder();
		for (final Entry<String, String> entry : params.entrySet()) {
			sb.append(entry.getKey()).append("=").append(entry.getValue());
		}
		final String paramsString = sb.toString();
		final String md5Secret = OkEncryptUtil.toMD5(mAccessToken + mAppSecret);
		final String sig = OkEncryptUtil.toMD5(paramsString + md5Secret);
		params.put(PARAM_SIGN, sig);
	}

	/* **** LOGOUT **** */

	/**
	 * Clears all token information from sdk
	 * 
	 * @param context
	 */
	public final void clearTokens(final Context context) {
		mAccessToken = null;
		mRefreshToken = null;
		TokenStore.removeStoredTokens(context);
	}

	/**
	 * Check if access token is available (can be used to check if previously used access token and refresh token was successfully loaded from the storage).
	 * 
	 * @return true if there is access token available.
	 *         <p>
	 *         <b>Note that if access token is available it doen't mean that it is still valid and not expired.</b>
	 */
	public final boolean hasAccessToken() {
		return mAccessToken != null;
	}

	/**
	 * Get currently used access token.
	 * 
	 * @return access token or <b>null</b> if no token is available.
	 */
	public final String getCurrentAccessToken() {
		return mAccessToken;
	}

	public final void setTokenRequestListener(final OkTokenRequestListener tokenRequestListener) {
		this.mTokenRequestListener = tokenRequestListener;
	}
	
	public final void removeTokenRequestListener() {
		this.mTokenRequestListener = null;
	}

}
