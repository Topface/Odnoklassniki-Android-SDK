package ru.ok.android.sdk;

final class Shared {

	static final String OAUTH_GET_CODE_URL = "https://odnoklassniki.ru/oauth/authorize?client_id=%s&response_type=%s&redirect_uri=%s&layout=m&tkn=%s";
	static final String OAUTH_GET_TOKEN_PATH = "oauth/token.do";
	static final String OAUTH_RESPONSE_TYPE = "code";

	static final String OAUTH_RDRCT_SCHEME = "okauth";
	static final String OAUTH_RDRCT_AUTH_PREFIX = "ok";
	static final String OAUTH_RDRCT_FULL_PREFIX = OAUTH_RDRCT_SCHEME + "://" + OAUTH_RDRCT_AUTH_PREFIX;

	// Actions
	static final int METHOD_AUTHORIZE = 1;

	// Messages
	static final int MESSAGE_AUTH_RESULT = 1337;

	// Params
	static final String PARAM_CLIENT_ID = "client_id";
	static final String PARAM_CLIENT_SECRET = "client_secret";
	static final String PARAM_GRANT_TYPE = "grant_type";
	static final String PARAM_REFRESH_TOKEN = "refresh_token";
	static final String PARAM_REDIRECT_URI = "redirect_uri";
	static final String PARAM_SCOPES = "scopes";
	static final String PARAM_CODE = "code";
	static final String PARAM_TOKEN_TYPE = "token_type";
	static final String PARAM_ACCESS_TOKEN = "access_token";
	static final String PARAM_ERROR = "error";
	static final String PARAM_SIGN = "sig";
	static final String PARAM_METHOD = "method";
	static final String PARAM_APP_KEY = "application_key";
	static final String PARAM_OAUTH_ONLY = "oauth_only";
	static final String PARAM_CANCELLED = "cancelled";
	static final String PARAM_TOKEN = "tkn";
	static final String PARAM_MESSENGER = "msngr";

	// Grants
	static final String GRANT_REFRESH_TOKEN = "refresh_token";
	static final String GRANT_AUTH_CODE = "authorization_code";

	// Api
	static final String API_URL_BASE = "http://api.odnoklassniki.ru/";
	static final String API_REQ_URL_PATH = "fb.do";

	static final String PREFERENCES_FILE = "oksdkprefs";

}
