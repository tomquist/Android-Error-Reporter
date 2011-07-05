/*
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the license at LICENSE.txt
 * or http://www.opensource.org/licenses/cddl1.php.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 * 
 * 
 * Copyright 2010 Tom Quist 
 * All rights reserved Use is subject to license terms.
 */
package de.quist.app.errorreporter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.net.ssl.SSLException;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

public class ExceptionReportService extends ReportingIntentService {

	static final String ACTION_SEND_REPORT = ExceptionReportService.class.getPackage().getName().concat(".actionSendReport");

	static final String EXTRA_STACK_TRACE = ExceptionReportService.class.getPackage().getName().concat(".extraStackTrace");
	static final String EXTRA_EXCEPTION_CLASS = ExceptionReportService.class.getPackage().getName().concat(".extraExceptionClass");
	static final String EXTRA_MESSAGE =  ExceptionReportService.class.getPackage().getName().concat(".extraMessage");
	static final String EXTRA_EXCEPTION_TIME =  ExceptionReportService.class.getPackage().getName().concat(".extraExceptionTime");
	static final String EXTRA_THREAD_NAME = ExceptionReportService.class.getPackage().getName().concat(".extraThreadName");
	static final String EXTRA_EXTRA_MESSAGE = ExceptionReportService.class.getPackage().getName().concat(".extraCustomMessage");
	static final String EXTRA_MANUAL_REPORT = ExceptionReportService.class.getPackage().getName().concat(".extraManualReport");
	static final String EXTRA_AVAILABLE_MEMORY = ExceptionReportService.class.getPackage().getName().concat(".extraAvailableMemory");
	static final String EXTRA_TOTAL_MEMORY = ExceptionReportService.class.getPackage().getName().concat(".extraTotalMemory");
	
	/**
	 * Used internally to count retries.
	 */
	private static final String EXTRA_CURRENT_RETRY_COUNT = ExceptionReportService.class.getPackage().getName().concat(".extraCurrentRetryCount");

	/**
	 * The default maximum backoff exponent.
	 */
	static final int DEFAULT_MAXIMUM_BACKOFF_EXPONENT = 12;

	/**
	 * The default maximum number of tries to send a report. This value results in a retry
	 * time of about 8 hours with an unchanged retry count.
	 */
	static final int DEFAULT_MAXIMUM_RETRY_COUNT = DEFAULT_MAXIMUM_BACKOFF_EXPONENT + 5;
	
	/**
	 * The default value whether to report on Android 2.2 and above.
	 */
	static final boolean DEFAULT_REPORT_ON_FROYO = false;

	private static final String TAG = ExceptionReportService.class.getSimpleName();

	/**
	 * Maximum number of tries to send a report. Default is {@value #DEFAULT_MAXIMUM_RETRY_COUNT}.
	 */
	private static final String META_DATA_MAXIMUM_RETRY_COUNT = ExceptionReportService.class.getPackage().getName().concat(".maximumRetryCount");
	private static final String META_DATA_MAXIMUM_BACKOFF_EXPONENT = ExceptionReportService.class.getPackage().getName().concat(".maximumBackoffExponent");
	private static final String META_DATA_REPORT_ON_FROYO = ExceptionReportService.class.getPackage().getName().concat(".reportOnFroyo");
	private static final String META_DATA_FIELDS_TO_SEND = ExceptionReportService.class.getPackage().getName().concat(".includeFields");

	private static final String DEFAULT_FIELDS_TO_SEND = "all";

	public ExceptionReportService() {
		super(ExceptionReportService.class.getSimpleName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			if (intent.getAction().equals(ACTION_SEND_REPORT)) {
				sendReport(intent);
			}
		} catch (Exception e) {
			// Catch all other exceptions as otherwise they would create an endless loop
			Log.e(TAG, "Error while sending an error report", e);
		}	
	}

	private void sendReport(Intent intent) throws UnsupportedEncodingException, NameNotFoundException {
		Log.v(TAG, "Got request to report error: " + intent.toString());
		Uri server = getTargetUrl();

		boolean isManualReport = intent.getBooleanExtra(EXTRA_MANUAL_REPORT, false);
		boolean isReportOnFroyo = isReportOnFroyo();
		boolean isFroyoOrAbove = isFroyoOrAbove();
		if (isFroyoOrAbove && !isManualReport && !isReportOnFroyo) {
			// We don't send automatic reports on froyo or above
			Log.d(TAG, "Don't send automatic report on froyo");
			return;
		}
		
		Set<String> fieldsToSend = getFieldsToSend();
		
		String stacktrace = intent.getStringExtra(EXTRA_STACK_TRACE);
		String exception = intent.getStringExtra(EXTRA_EXCEPTION_CLASS);
		String message = intent.getStringExtra(EXTRA_MESSAGE);
		long availableMemory = intent.getLongExtra(EXTRA_AVAILABLE_MEMORY, -1l);
		long totalMemory = intent.getLongExtra(EXTRA_TOTAL_MEMORY, -1l);
		String dateTime = intent.getStringExtra(EXTRA_EXCEPTION_TIME);
		String threadName = intent.getStringExtra(EXTRA_THREAD_NAME);
		String extraMessage = intent.getStringExtra(EXTRA_EXTRA_MESSAGE);
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		addNameValuePair(params, fieldsToSend, "exStackTrace", stacktrace);
		addNameValuePair(params, fieldsToSend, "exClass", exception);
		addNameValuePair(params, fieldsToSend, "exDateTime", dateTime);
		addNameValuePair(params, fieldsToSend, "exMessage", message);
		addNameValuePair(params, fieldsToSend, "exThreadName", threadName);
		if (extraMessage != null) addNameValuePair(params, fieldsToSend, "extraMessage", extraMessage);
		if (availableMemory >= 0) addNameValuePair(params, fieldsToSend, "devAvailableMemory", availableMemory+"");
		if (totalMemory >= 0) addNameValuePair(params, fieldsToSend, "devTotalMemory", totalMemory+"");
		
		PackageManager pm = getPackageManager();
		try {
			PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
			addNameValuePair(params, fieldsToSend, "appVersionCode", packageInfo.versionCode+"");
			addNameValuePair(params, fieldsToSend, "appVersionName", packageInfo.versionName);
			addNameValuePair(params, fieldsToSend, "appPackageName", packageInfo.packageName);
		} catch (NameNotFoundException e) {}
		addNameValuePair(params, fieldsToSend, "devModel", android.os.Build.MODEL);
		addNameValuePair(params, fieldsToSend, "devSdk", android.os.Build.VERSION.SDK);
		addNameValuePair(params, fieldsToSend, "devReleaseVersion", android.os.Build.VERSION.RELEASE);

		HttpClient httpClient = new DefaultHttpClient();
		HttpPost post = new HttpPost(server.toString());
		post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
		Log.d(TAG, "Created post request");

		try {
			httpClient.execute(post);
			Log.v(TAG, "Reported error: " + intent.toString());
		} catch (ClientProtocolException e) {
			// Ignore this kind of error
			Log.e(TAG, "Error while sending an error report", e);
		} catch (SSLException e) {
			Log.e(TAG, "Error while sending an error report", e);
		} catch (IOException e) {
			if (e instanceof SocketException && e.getMessage().contains("Permission denied")) {
				Log.e(TAG, "You don't have internet permission", e);
			} else {
				int maximumRetryCount = getMaximumRetryCount();
				int maximumExponent = getMaximumBackoffExponent();
				// Retry at a later point in time
				AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
				PendingIntent operation = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
				int exponent = intent.getIntExtra(EXTRA_CURRENT_RETRY_COUNT, 0);
				intent.putExtra(EXTRA_CURRENT_RETRY_COUNT, exponent + 1);
				if (exponent >= maximumRetryCount) {
					// Discard error
					Log.w(TAG, "Error report reached the maximum retry count and will be discarded.\nStacktrace:\n"+stacktrace);
					return;
				}
				if (exponent > maximumExponent) {
					exponent = maximumExponent;
				}
				long backoff = (1 << exponent) * 1000; // backoff in ms
				alarmMgr.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + backoff, operation);
			}
		}
	}

	private void addNameValuePair(List<NameValuePair> list, Set<String> fieldsToSend, String name, String value) {
		if (fieldsToSend.contains("all") || fieldsToSend.contains(name)) {
			list.add(new BasicNameValuePair(name, value));
		}
		
	}
	
	private boolean isFroyoOrAbove() {
		int sdk = getSdkInt();
		return sdk >= 8;
	}
	
	private int getSdkInt() {
		String sdk = android.os.Build.VERSION.SDK;
		try {
			return Integer.parseInt(sdk);
		} catch (NumberFormatException e) {
			return 1000; // Development version
		}
	}

	public Uri getTargetUrl() throws NameNotFoundException {
		ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);

		ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
		if (ai.metaData == null) throw new IllegalArgumentException(ExceptionReportService.class.getPackage().getName().concat("targetUrl is undefined"));
		String key = ExceptionReportService.class.getPackage().getName().concat(".targetUrl");
		String urlString = null;
		if (info.metaData.containsKey(key)) {
			Object url = info.metaData.get(key);
			if (url instanceof String) {
				urlString = (String) url;
			} else if (url instanceof Integer) {
				int urlResId = info.metaData.getInt(key);
				urlString = getString(urlResId);
			}
		}
		if (urlString == null) {
			throw new IllegalArgumentException(ExceptionReportService.class.getPackage().getName().concat("targetUrl is undefined"));
		}
		return Uri.parse(urlString);
	}

	public int getMaximumRetryCount() throws NameNotFoundException {
		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			return ai.metaData.getInt(META_DATA_MAXIMUM_RETRY_COUNT, DEFAULT_MAXIMUM_RETRY_COUNT);
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}

	public int getMaximumBackoffExponent() throws NameNotFoundException {
		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			return ai.metaData.getInt(META_DATA_MAXIMUM_BACKOFF_EXPONENT, DEFAULT_MAXIMUM_BACKOFF_EXPONENT);
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}
	
	public Set<String> getFieldsToSend() throws NameNotFoundException {
		try {
			HashSet<String> result = new HashSet<String>();
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			String fields = ai.metaData.getString(META_DATA_FIELDS_TO_SEND);
			if (fields == null) fields = DEFAULT_FIELDS_TO_SEND;
			StringTokenizer st = new StringTokenizer(fields, ",");
			while (st.hasMoreTokens()) {
				result.add(st.nextToken());
			}
			return result;
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}
	
	public boolean isReportOnFroyo() throws NameNotFoundException {
		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			return ai.metaData.getBoolean(META_DATA_REPORT_ON_FROYO, DEFAULT_REPORT_ON_FROYO);
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}

}
