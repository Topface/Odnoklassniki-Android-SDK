package ru.ok.android.sdk;

import static ru.ok.android.sdk.Shared.PREFERENCES_FILE;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

class TokenStore {
	
	private static final String PREF_ACCESS_TOKEN = "acctkn";
	private static final String PREF_REFRESH_TOKEN = "reftkn";
	
	static final void storeTokens(final Context context, final String accessToken, final String refreshToken) {
		final SharedPreferences prefs = getPreferences(context);
		final Editor editor = prefs.edit();
		editor.putString(PREF_ACCESS_TOKEN, accessToken);
		editor.putString(PREF_REFRESH_TOKEN, refreshToken);
		editor.commit();
	}
	
	static final void storeAccessToken(final Context context, final String accessToken) {
		final SharedPreferences prefs = getPreferences(context);
		final Editor editor = prefs.edit();
		editor.putString(PREF_ACCESS_TOKEN, accessToken);
		editor.commit();
	}
	
	static final void storeRefreshToken(final Context context, final String refreshToken) {
		final SharedPreferences prefs = getPreferences(context);
		final Editor editor = prefs.edit();
		editor.putString(PREF_REFRESH_TOKEN, refreshToken);
		editor.commit();
	}
	
	static final void removeStoredTokens(final Context context) {
		final SharedPreferences prefs = getPreferences(context);
		final Editor editor = prefs.edit();
		editor.remove(PREF_ACCESS_TOKEN);
		editor.remove(PREF_REFRESH_TOKEN);
		editor.commit();
	}
	
	static final String getStoredAccessToken(final Context context) {
		final SharedPreferences prefs = getPreferences(context);
		return prefs.getString(PREF_ACCESS_TOKEN, null);
	}
	
	static final String getStoredRefreshToken(final Context context) {
		final SharedPreferences prefs = getPreferences(context);
		return prefs.getString(PREF_REFRESH_TOKEN, null);
	}
	
	static final SharedPreferences getPreferences(final Context context) {
		return context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
	}

}
