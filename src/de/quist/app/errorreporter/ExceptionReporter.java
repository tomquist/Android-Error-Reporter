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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class ExceptionReporter {

	private static final String TAG = ExceptionReporter.class.getSimpleName();
	
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
				reportException(thread, ex);
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
		reportException(thread, ex, null);
	}
	
	/**
	 * Sends an error report with an extra message.
	 * 
	 * @param thread The thread where the exception occurred (e.g. {@link java.lang.Thread#currentThread()})
	 * @param ex The exception
	 */
	public void reportException(Thread thread, Throwable ex, String extraMessage) {
		final Writer writer = new StringWriter();
	    final PrintWriter pWriter = new PrintWriter(writer);
	    ex.printStackTrace(pWriter);
	    String stackTrace = writer.toString();
	
		Intent intent = new Intent();
		intent.setClass(context, ExceptionReportService.class);
		intent.setAction(ExceptionReportService.ACTION_SEND_REPORT);
		intent.putExtra(ExceptionReportService.EXTRA_THREAD_NAME, thread.getName());
		intent.putExtra(ExceptionReportService.EXTRA_STACK_TRACE, stackTrace);
		intent.putExtra(ExceptionReportService.EXTRA_MESSAGE, ex.getMessage());
		if (extraMessage != null) intent.putExtra(ExceptionReportService.EXTRA_EXTRA_MESSAGE, extraMessage);
		
		ComponentName service = context.startService(intent);
		if (service == null) {
			Log.e(TAG, "Service has not be added to your AndroidManifest.xml\n" +
					"Add the following line to your manifest:\n" +
					"<service android:name=\""+ExceptionReportService.class.getName()+"\" android:process=\":errorReporter\"/>");
		}
	}
	
}
