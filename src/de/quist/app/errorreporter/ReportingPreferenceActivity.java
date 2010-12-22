package de.quist.app.errorreporter;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ReportingPreferenceActivity extends PreferenceActivity {

	private ExceptionReporter exceptionReporter;

	protected ExceptionReporter getExceptionReporter() {
		return exceptionReporter;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		exceptionReporter = ExceptionReporter.register(this);
		super.onCreate(savedInstanceState);
	}
}
