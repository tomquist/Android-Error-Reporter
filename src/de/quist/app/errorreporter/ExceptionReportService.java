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
import java.util.ArrayList;
import java.util.List;

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

	static final String ACTION_SEND_REPORT = ExceptionReportService.class.getName().concat(".actionSendReport");

	static final String EXTRA_STACK_TRACE = ExceptionReportService.class.getName().concat(".extraStackTrace");
	static final String EXTRA_MESSAGE =  ExceptionReportService.class.getName().concat(".extraMessage");
	static final String EXTRA_THREAD_NAME = ExceptionReportService.class.getName().concat(".extraThreadName");
	static final String EXTRA_EXTRA_MESSAGE = ExceptionReportService.class.getName().concat(".extraCustomMessage");

	private static final String EXTRA_CURRENT_RETRY_COUNT = ExceptionReportService.class.getName().concat(".extraCurrentRetryCount");

	/**
	 * The default maximum backoff exponent.
	 */
	static final int DEFAULT_MAXIMUM_BACKOFF_EXPONENT = 12;

	/**
	 * The default maximum number of tries to send a report. This value results in a retry
	 * time of about 8 hours with an unchanged retry count.
	 */
	static final int DEFAULT_MAXIMUM_RETRY_COUNT = DEFAULT_MAXIMUM_BACKOFF_EXPONENT + 5;

	private static final String TAG = ExceptionReportService.class.getSimpleName();

	/**
	 * Maximum number of tries to send a report. Default is {@value #DEFAULT_MAXIMUM_RETRY_COUNT}.
	 */
	private static final String META_DATA_MAXIMUM_RETRY_COUNT = ExceptionReportService.class.getPackage().getName().concat(".maximumRetryCount");
	private static final String META_DATA_MAXIMUM_BACKOFF_EXPONENT = ExceptionReportService.class.getPackage().getName().concat(".maximumBackoffExponent");

	public ExceptionReportService() {
		super(ExceptionReportService.class.getSimpleName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		//android.os.Debug.waitForDebugger();
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
		Log.d(TAG, "Got request to report error: " + intent.toString());
		Uri server = getTargetUrl();

		String stacktrace = intent.getStringExtra(EXTRA_STACK_TRACE);
		String message = intent.getStringExtra(EXTRA_MESSAGE);
		String threadName = intent.getStringExtra(EXTRA_THREAD_NAME);
		String extraMessage = intent.getStringExtra(EXTRA_EXTRA_MESSAGE);
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("stackTrace", stacktrace));
		params.add(new BasicNameValuePair("message", message));
		params.add(new BasicNameValuePair("threadName", threadName));
		if (extraMessage != null) params.add(new BasicNameValuePair("extraMessage", extraMessage));

		PackageManager pm = getPackageManager();
		try {
			PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
			params.add(new BasicNameValuePair("versionCode", packageInfo.versionCode+""));
			params.add(new BasicNameValuePair("versionName", packageInfo.versionName));
			params.add(new BasicNameValuePair("packageName", packageInfo.packageName));
		} catch (NameNotFoundException e) {}
		params.add(new BasicNameValuePair("model", android.os.Build.MODEL));
		params.add(new BasicNameValuePair("releaseVersion", android.os.Build.VERSION.RELEASE));

		HttpClient httpClient = new DefaultHttpClient();
		HttpPost post = new HttpPost(server.toString());
		post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));

		try {
			httpClient.execute(post);
		} catch (ClientProtocolException e) {
			// Ignore this kind of error
			Log.e(TAG, "Error while sending an error report", e);
		} catch (SSLException e) {
			Log.e(TAG, "Error while sending an error report", e);
		} catch (IOException e) {
			int maximumRetryCount = getMaximumRetryCount();
			int maximumExponent = getMaximumBackoffExponent();
			// Retry at a later point in time
			AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
			PendingIntent operation = PendingIntent.getService(this, 0, intent, 0);
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

	public Uri getTargetUrl() throws NameNotFoundException {
		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			String urlString = ai.metaData.getString(ExceptionReportService.class.getPackage().getName().concat(".targetUrl"));
			return Uri.parse(urlString);
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}

	public int getMaximumRetryCount() throws NameNotFoundException {
		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			if (ai.metaData.containsKey(META_DATA_MAXIMUM_RETRY_COUNT)) {
				return ai.metaData.getInt(META_DATA_MAXIMUM_RETRY_COUNT);
			} else {
				return DEFAULT_MAXIMUM_RETRY_COUNT;
			}
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}

	public int getMaximumBackoffExponent() throws NameNotFoundException {
		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
			if (ai.metaData.containsKey(META_DATA_MAXIMUM_BACKOFF_EXPONENT)) {
				return ai.metaData.getInt(META_DATA_MAXIMUM_BACKOFF_EXPONENT);
			} else {
				return DEFAULT_MAXIMUM_BACKOFF_EXPONENT;
			}
		} catch (NameNotFoundException e) {
			// Should never happen
			throw e;
		} 
	}

}
