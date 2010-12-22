package de.quist.app.errorreporter;

import android.app.AliasActivity;
import android.os.Bundle;

public class ReportingAliasActivity extends AliasActivity {

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
