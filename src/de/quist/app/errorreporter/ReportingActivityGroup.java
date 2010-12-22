package de.quist.app.errorreporter;

import android.app.ActivityGroup;
import android.os.Bundle;

public class ReportingActivityGroup extends ActivityGroup {

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
