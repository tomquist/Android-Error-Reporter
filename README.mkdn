Android Error-Reporter
======================

Integration
------------
To integrate **Android Error-Reporter** just follow four simple steps:

1. Download android-error-reporter.jar and add it to your build path

2. Inherit from one of the base classes `Reporting*` (e.g. `ReportingActivity`, `ReportingService` or `ReportingIntentService`) or register your context in every `onCreate()` of your activity or context, e.g.: 

    
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            ExceptionReporter reporter = ExceptionReporter.register(this);
            super.onCreate(savedInstanceState);
        }
    

3. Add the `ExceptionReportService` to your `AndroidManifest.xml` (within the `<application/>` node):

		<service
			android:name="de.quist.app.errorreporter.ExceptionReportService"
			android:process=":exceptionReporter"/>

4. Configure the target URL where to send the errors by adding the following line to your `AndroidManifest.xml` (within the `<application/>` node):

		<meta-data
			android:name="de.quist.app.errorreporter.targetUrl"
			android:value="http://foo.bar/error.php" />
    
    
Usage
-----

### Report catched exceptions

Unhandled exceptions are automatically handled by the ExceptionReportService. You can also report
catched exceptions by calling `exceptionHandler.reportException(thread, e)` or
`exceptionHandler.reportException(thread, e, extraMessage)` on the ExceptionReporter
object returned by `ExceptionReporter.register(context)`. 
If your activity/service inherits from one of the supplied base classes (`de.quist.app.errorreporter.Reporting*`), you can access the
ExceptionReporter via `getExceptionReporter()`.

### User approved error reporting

By default errors are sent to the server automatically without asking the user for permission. This can be
changed by adding the `ExceptionReportActivity` to your `AndroidManifest.xml`. To do so, add the following line:

	<activity
		android:name="de.quist.app.errorreporter.ExceptionReportActivity"
		android:process=":exceptionReporter"
		android:theme="@android:style/Theme.NoDisplay"/>		

<table>
	<tr>
		<td><img src="https://github.com/tomquist/Android-Error-Reporter/raw/master/README_images/notification.png"/></td>
		<td><img src="https://github.com/tomquist/Android-Error-Reporter/raw/master/README_images/dialog.png"/></td>
		<td><img src="https://github.com/tomquist/Android-Error-Reporter/raw/master/README_images/dialog-message.png"/></td>
	</tr>
</table>

The exception reporter will automatically detect the defined activity and instead of automatically sending the
report a notification is created which will ask the user for permission to send the notification when clicked.
You have full control over the texts and icons of both, the notification and the report-dialog. All resources
can be specified by adding meta-data tags to your `AndroidManifest.xml` (within the `<application/>` node) which reference to a string/drawable in
their `android:resource`-attribute, e.g.:

	<meta-data
		android:name="de.quist.app.errorreporter.dialogMessageHint"
		android:resource="@string/error_reporting_message_hint"/>

See attributes `de.quist.app.errorreporter.dialog*` and `de.quist.app.errorreporter.notification*` in the section __Configuration__ for more information abouth how to control the notification and dialog.

### Retry-Rules

The ExceptionReportService tries to send the error to the URL specified in your `AndroidManifest.xml`.
If it fails it retries with an exponential back-off. The default configuration will increase the back-off
up to 2^12 sec (about 1h8m) and will retry it 17 times until it gives up (this will result in a total
time span of 1s+2s+4s+8s+...2^12 s*5=8h). You can change these values by adding specific meta-data nodes to your `AndroidManifest.xml` (see __Configuration__)

Configuration
-------------
You can add the following name/value pairs as a meta-data node to your `AndroidManifest.xml` (within the `<application/>` node).

__All names need to have `de.quist.app.errorreporter.` as a prefix__!!!:

<table border="1">
	<tr>
		<th><b>Name</b></th>
		<th><b>Type</b></th>
		<th><b>Default</b></th>
		<th><b>Description</b></th>
	<tr>
	<tr>
		<td><tt>maximumRetryCount</tt></td>
		<td>int</td>
		<td><tt>17</tt></td>
		<td>Maximum number of tries to send an error report</td>
	<tr>
	<tr>
		<td><tt>maximumBackoffExponent</tt></td>
		<td>int</td>
		<td><tt>12</tt></td>
		<td>Maximum exponent for the back-off</td>
	<tr>
	<tr>
		<td><tt>reportOnFroyo</tt></td>
		<td>boolean</td>
		<td><tt>false</tt></td>
		<td>Defines whether unhandled exception are reported on Android 2.2 (Froyo) and above or not, since Froyo has its own error-reporting system</td>
	<tr>
	<tr>
		<td><tt>includeFields</tt></td>
		<td>String</td>
		<td><tt>all</tt></td>
		<td>Comma-separated list of fields to send (field names as of section <b>Server</b>). If the list contains <tt>all</tt>, all available fields will be included</td>
	<tr>
	<tr>
		<td><tt>dialogIcon</tt></td>
		<td>int</td>
		<td><tt>@android:drawable/ic_dialog_alert</tt></td>
		<td>The icon of the dialog for user approved error reports. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>dialogTitle</tt></td>
		<td>int</td>
		<td><tt>^1 crashed</tt></td>
		<td>The dialog title for user approved error reports. You can use a template style text (<tt>^1</tt>) where the first placeholder will be replaced by the application name. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>dialogText</tt></td>
		<td>int</td>
		<td><tt>^1 crashed because of an unexpected error. Please help fixing the error by sending an error report.</tt></td>
		<td>The text shown in the dialog for user approved error reports. You can use a template style text (<tt>^1</tt>) where the first placeholder will be replaced by the application name. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>dialogMessageHint</tt></td>
		<td>int</td>
		<td><i>Undefined</i></td>
		<td>If you specify this value, an additional text-input field will be shown in the dialog with the specified message as hint. The content will be sent as additional information in the <tt>extraMessage</tt> field (see section <b>Server</b>). <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>dialogSendButton</tt></td>
		<td>int</td>
		<td><tt>@android:string/ok</tt></td>
		<td>Text on the send-button of the dialog for user approved error reports. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>dialogCancelButton</tt></td>
		<td>int</td>
		<td><tt>@android:string/cancel</tt></td>
		<td>Text on the cancel-button of the dialog for user approved error reports. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</></td>
	<tr>
	<tr>
		<td><tt>notificationIcon</tt></td>
		<td>int</td>
		<td><tt>@android:drawable/stat_notify_error</tt></td>
		<td>Icon of the notification for user approved error reports. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>notificationTitle</tt></td>
		<td>int</td>
		<td><tt>^1 crashed</tt></td>
		<td>The notification title for user approved error reports. You can use a template style text (<tt>^1</tt>) where the first placeholder will be replaced by the application name. <i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>notificationText</tt></td>
		<td>int</td>
		<td><tt>Click here to help fixing the issue</tt></td>
		<td>The notification text for user approved error reports. You can use a template style text (<tt>^1</tt>) where the first placeholder will be replaced by the application name.<i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
	<tr>
		<td><tt>notificationTickerText</tt></td>
		<td>int</td>
		<td><i>Empty<i></td>
		<td>The notification ticker text for user approved error reports. You can use a template style text (<tt>^1</tt>) where the first placeholder will be replaced by the application name.<i>(Use the <tt>android:resource</tt> attribute and reference to an existing resource)</i></td>
	<tr>
</table>
		


Server
------
The server will receive error reports via HTTP-post requests. They will contain the following fields:

<table>
	<tr>
		<td><tt>exStackTrace</tt></td>
		<td>The stack trace</td>
	</tr>
	<tr>
		<td><tt>exClass</tt></td>
		<td>The exception class</td>
	</tr>
    <tr>
        <td><tt>exMessage</tt></td>
        <td>The exceptions message</td>
    </tr>
    <tr>
        <td><tt>exDateTime</tt></td>
        <td>The date and time when the exception happend in the format "yyyy-MM-dd HH:mm:ssZ" (SimpleDateFormat)</td>
    </tr>
    <tr>
        <td><tt>extraMessage</tt></td>
        <td>A custom message which can be added to manual error reports</td>
    </tr>
    <tr>
        <td><tt>exThreadName</tt></td>
        <td>The name of the thread the error has been thrown in</td>
    </tr>
    <tr>
        <td><tt>appVersionCode</tt></td>
        <td>The version code (as defined in your AndroidManifest.xml)</td>
    </tr>
    <tr>
        <td><tt>appVersionName</tt></td>
        <td>The version name (as defined in your AndroidManifest.xml)</td>
    </tr>
    <tr>
        <td><tt>appPackageName</tt></td>
        <td>The package name (as defined in your AndroidManifest.xml)</td>
    </tr>
    <tr>
        <td><tt>devAvailableMemory</tt></td>
        <td>The devices available memory in bytes</td>
    </tr>
    <tr>
        <td><tt>devTotalMemory</tt></td>
        <td>The devices total memory in bytes</td>
    </tr>
    <tr>
        <td><tt>devModel</tt></td>
        <td>The phones model name (<tt>android.os.Build.MODEL</tt>)</td>
    </tr>
    <tr>
        <td><tt>devSdk</tt></td>
        <td>The phones sdk version (<tt>android.os.Build.VERSION.SDK</tt>)</td>
    </tr>
    <tr>
        <td><tt>devReleaseVersion</tt></td>
        <td>The phones release version (<tt>android.os.Build.VERSION.RELEASE</tt>)</td>
    </tr>
</table>