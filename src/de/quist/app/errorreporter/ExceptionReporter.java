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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

public final class ExceptionReporter {

	private static final String TAG = ExceptionReporter.class.getSimpleName();
	private static final String META_DATA_NOTIFICATION_ICON = ExceptionReporter.class.getPackage().getName().concat(".notificationIcon");
	private static final String META_DATA_NOTIFICATION_TITLE = ExceptionReporter.class.getPackage().getName().concat(".notificationTitle");
	private static final String META_DATA_NOTIFICATION_TEXT = ExceptionReporter.class.getPackage().getName().concat(".notificationText");
	private static final String META_DATA_NOTIFICATION_TICKER_TEXT = ExceptionReporter.class.getPackage().getName().concat(".notificationTickerText");
	
	private static final int DEFAULT_NOTIFICATION_ICON = android.R.drawable.stat_notify_error;
	private static final CharSequence DEFAULT_NOTIFICATION_TITLE = "^1 crashed";
	private static final CharSequence DEFAULT_NOTIFICATION_TEXT = "Click here to help fixing the issue";
	private static final CharSequence DEFAULT_NOTIFICATION_TICKER_TEXT = "";

	/**
	 * Registers this context and returns an error handler object
	 * to be able to manually report errors.
	 * 
	 * @param context The context
	 * @return The error handler which can be used to manually report errors
	 */
	public static ExceptionReporter register(Context context) {
		UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
		if (handler instanceof Handler) {
			Handler errHandler = (Handler) handler;
			errHandler.errorHandler.setContext(context);
			return errHandler.errorHandler;
		} else {
			ExceptionReporter errHandler = new ExceptionReporter(handler, context);
			Thread.setDefaultUncaughtExceptionHandler(errHandler.handler);
			return errHandler;
		}
	}

	private void setContext(Context context) {
		if (context.getApplicationContext() != null) {
			this.context = context.getApplicationContext();
		} else {
			this.context = context;
		}
	}

	private Context context;
	private Handler handler;
	private ApplicationInfo applicationInfo;

	private ExceptionReporter(UncaughtExceptionHandler defaultHandler, Context context) {
		this.handler = new Handler(defaultHandler);
		this.setContext(context);
	}

	private class Handler implements UncaughtExceptionHandler {

		private UncaughtExceptionHandler subject;
		private ExceptionReporter errorHandler;

		private Handler(UncaughtExceptionHandler subject) {
			this.subject = subject;
			this.errorHandler = ExceptionReporter.this;
		}

		@Override
		public void uncaughtException(Thread thread, Throwable ex) {
			try {
				reportException(thread, ex, null, false);
			} catch (Exception e) {
				Log.e(TAG, "Error while reporting exception", e);
			}
			subject.uncaughtException(thread, ex);
		}

	}

	/**
	 * Sends an error report.
	 * 
	 * @param thread The thread where the exception occurred (e.g. {@link java.lang.Thread#currentThread()})
	 * @param ex The exception
	 */
	public void reportException(Thread thread, Throwable ex) {
		reportException(thread, ex, null, true);
	}

	/**
	 * Sends an error report with an extra message.
	 * 
	 * @param thread The thread where the exception occurred (e.g. {@link java.lang.Thread#currentThread()})
	 * @param ex The exception
	 */
	public void reportException(Thread thread, Throwable ex, String extraMessage) {
		reportException(thread, ex, extraMessage, true);
	}
	
	private void reportException(Thread thread, Throwable ex, String extraMessage, boolean manual) {
		final Writer writer = new StringWriter();
		final PrintWriter pWriter = new PrintWriter(writer);
		ex.printStackTrace(pWriter);
		String stackTrace = writer.toString();

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
		
		Intent intent = new Intent();
		intent.setData((Uri.parse("custom://" + SystemClock.elapsedRealtime()))); // Makes the intent unique 
		intent.setAction(ExceptionReportService.ACTION_SEND_REPORT);
		intent.putExtra(ExceptionReportService.EXTRA_THREAD_NAME, thread.getName());
		intent.putExtra(ExceptionReportService.EXTRA_EXCEPTION_CLASS, ex.getClass().getName());
		intent.putExtra(ExceptionReportService.EXTRA_EXCEPTION_TIME, format.format(new Date()));
		intent.putExtra(ExceptionReportService.EXTRA_STACK_TRACE, stackTrace);
		intent.putExtra(ExceptionReportService.EXTRA_MESSAGE, ex.getMessage());
		intent.putExtra(ExceptionReportService.EXTRA_MANUAL_REPORT, manual);
		intent.putExtra(ExceptionReportService.EXTRA_AVAILABLE_MEMORY, getAvailableInternalMemorySize());
		intent.putExtra(ExceptionReportService.EXTRA_TOTAL_MEMORY, getTotalInternalMemorySize());
		if (extraMessage != null) intent.putExtra(ExceptionReportService.EXTRA_EXTRA_MESSAGE, extraMessage);

		intent.setClass(context, ExceptionReportActivity.class);
		List<ResolveInfo> resolvedActivities = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		if (!resolvedActivities.isEmpty()) {
			Log.v(TAG, ExceptionReportActivity.class.getSimpleName() + " is registered. Generating notification...");
			Notification notification = new Notification();
			notification.icon = getNotificationIcon();
			notification.tickerText = getNotificationTickerText();
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			notification.setLatestEventInfo(context, getNotificationTitle(), getNotificationMessage(), PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT));
			NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(new Random().nextInt(), notification);
		} else {
			intent.setClass(context, ExceptionReportService.class);
			ComponentName service = context.startService(intent);
			if (service == null) {
				Log.e(TAG, "Service has not be added to your AndroidManifest.xml\n" +
						"Add the following line to your manifest:\n" +
						"<service android:name=\""+ExceptionReportService.class.getName()+"\" android:process=\":exceptionReporter\"/>");
			}
		}
	}
	
	private CharSequence getNotificationTickerText() {
		CharSequence result = DEFAULT_NOTIFICATION_TICKER_TEXT;
		ApplicationInfo info = getApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_NOTIFICATION_TICKER_TEXT)) {
			int resId = info.metaData.getInt(META_DATA_NOTIFICATION_TICKER_TEXT);
			result = context.getText(resId);
		}
		return TextUtils.expandTemplate(result, context.getPackageManager().getApplicationLabel(info));
	}

	private int getNotificationIcon() {
		int result = DEFAULT_NOTIFICATION_ICON;
		ApplicationInfo info = getApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_NOTIFICATION_ICON)) {
			result = info.metaData.getInt(META_DATA_NOTIFICATION_ICON);
		}
		return result;
	}

	private CharSequence getNotificationTitle() {
		CharSequence result = DEFAULT_NOTIFICATION_TITLE;
		ApplicationInfo info = getApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_NOTIFICATION_TITLE)) {
			int resId = info.metaData.getInt(META_DATA_NOTIFICATION_TITLE);
			result = context.getText(resId);
		}
		return TextUtils.expandTemplate(result, context.getPackageManager().getApplicationLabel(info));
	}
	
	private CharSequence getNotificationMessage() {
		CharSequence result = DEFAULT_NOTIFICATION_TEXT;
		ApplicationInfo info = getApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_NOTIFICATION_TEXT)) {
			int resId = info.metaData.getInt(META_DATA_NOTIFICATION_TEXT);
			result = context.getText(resId);
		}
		return TextUtils.expandTemplate(result, context.getPackageManager().getApplicationLabel(info));
	}
	
	private ApplicationInfo getApplicationInfo() {
		if (this.applicationInfo != null) return this.applicationInfo;
		ApplicationInfo info;
		try {
			info = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
			this.applicationInfo = info;
			return info;
		} catch (NameNotFoundException e) {
			return null;
		}
	}
	
	private long getAvailableInternalMemorySize() { 
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath()); 
        return stat.getAvailableBlocks() * stat.getBlockSize(); 
    } 
     
    private long getTotalInternalMemorySize() { 
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath()); 
        return stat.getBlockCount() * stat.getBlockSize(); 
    } 

}
