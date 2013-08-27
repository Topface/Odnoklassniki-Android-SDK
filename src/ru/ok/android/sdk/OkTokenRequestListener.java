package ru.ok.android.sdk;

import android.content.Context;

/**
 * Listener methods are guaranteed to be called on the main (UI) thread.
 */
public interface OkTokenRequestListener {
	/**
	 * @param accessToken -
	 *            the access token for API methods usage
	 *            <p>
	 *            <b>The lifetime of the access token is 30 minutes. After expiration you should refresh it by using {@link Odnoklassniki#refreshToken(Context)}.</b>
	 *            <p>
	 *            <b>Note that access token is automatically set, cached and restored by {@link Odnoklassniki} itself, so, normally, developers working through SDK have no direct use for it.</b>
	 */
	public void onSuccess(final String accessToken);
	
	/**
	 * Request to get access token was unsuccessful due any reason.
	 */
	public void onError();
	
	/**
	 * User has cancelled authorization process.
	 */
	public void onCancel();
}
