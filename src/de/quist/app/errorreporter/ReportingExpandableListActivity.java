package de.quist.app.errorreporter;

import android.app.ExpandableListActivity;
import android.os.Bundle;

public class ReportingExpandableListActivity extends ExpandableListActivity {

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
