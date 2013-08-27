package ru.ok.android.sdk.util;

/**
 * A list of currently available scopes
 * <p>
 * Methods <b>users.getLoggedInUser</b> и <b>users.getCurrentUser</b> do not require any scopes
 * 
 */
public class OkScope {

	/**
	 * Grants access to API methods.
	 */
	public static final String VALUABLE_ACCESS = "VALUABLE ACCESS";
	
	/**
	 * Grants permission to set user status.
	 */
	public static final String SET_STATUS = "SET STATUS";
	
	/**
	 * Grants access to photo content.
	 */
	public static final String PHOTO_CONTENT = "PHOTO CONTENT";

}
