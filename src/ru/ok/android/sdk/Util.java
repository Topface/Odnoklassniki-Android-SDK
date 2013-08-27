package ru.ok.android.sdk;

import org.json.JSONException;
import org.json.JSONObject;

class Util {
	
	static final String[] parseTokenResponse(final String response, final boolean refresh) {
		String[] result = null;
		try {
			final JSONObject json = new JSONObject(response);
			final String accessToken = json.getString(Shared.PARAM_ACCESS_TOKEN);
			//final String tokenType = json.getString(Shared.PARAM_TOKEN_TYPE);
			result = new String[2];
			result[0] = accessToken;
			if (!refresh) {
				final String refreshToken = json.getString(Shared.PARAM_REFRESH_TOKEN);
				result[1] = refreshToken;
			}
		} catch (JSONException e) {
		}
		return result;
	}

}
