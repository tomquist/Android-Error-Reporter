package de.quist.app.errorreporter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ExceptionReportActivity extends Activity {

	private static final String META_DATA_DIALOG_TITLE = ExceptionReportActivity.class.getPackage().getName().concat(".dialogTitle");
	private static final String META_DATA_DIALOG_TEXT = ExceptionReportActivity.class.getPackage().getName().concat(".dialogText");
	private static final String META_DATA_DIALOG_ICON = ExceptionReportActivity.class.getPackage().getName().concat(".dialogIcon");
	private static final String META_DATA_DIALOG_MESSAGE_HINT = ExceptionReportActivity.class.getPackage().getName().concat(".dialogMessageHint");
	private static final String META_DATA_DIALOG_SEND_BUTTON = ExceptionReportActivity.class.getPackage().getName().concat(".dialogSendButton");
	private static final String META_DATA_DIALOG_CANCEL_BUTTON = ExceptionReportActivity.class.getPackage().getName().concat(".dialogCancelButton");
	
	private static final CharSequence DEFAULT_DIALOG_TITLE = "^1 crashed";
	private static final CharSequence DEFAULT_DIALOG_TEXT = "^1 crashed because of an unexpected error. Please help fixing the error by sending an error report.";
	private static final int DEFAULT_DIALOG_ICON = android.R.drawable.ic_dialog_alert;
	private static final int DEFAULT_POSITIVE_BUTTON_TEXT = android.R.string.ok;
	private static final int DEFAULT_NEGATIVE_BUTTON_TEXT = android.R.string.cancel;
	
	private ApplicationInfo info;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTheme(android.R.style.Theme_NoDisplay);
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle(getDialogTitle());
		dialog.setIcon(getDialogIcon());
		CharSequence messageHint = getDialogMessageHint();
		EditText textEdit = null;
		if (messageHint == null) {
			dialog.setMessage(getDialogText());
		} else {
			LinearLayout layout = new LinearLayout(this);
			float scale = getResources().getDisplayMetrics().density;
			int padding = (int) (5f * scale + 0.5f);
			layout.setPadding(padding, padding, padding, padding);
			layout.setOrientation(LinearLayout.VERTICAL);
			TextView textView = new TextView(this);
			textView.setText(getDialogText());
			layout.addView(textView, LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			textEdit = new EditText(this);
			textEdit.setHint(getDialogMessageHint());
			layout.addView(textEdit, LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			dialog.setView(layout);
		}
		final EditText text = textEdit;
		dialog.setPositiveButton(getDialogPositiveButtonText(), new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent i = getIntent();
				i.setClass(ExceptionReportActivity.this, ExceptionReportService.class);
				if (text != null && !TextUtils.isEmpty(text.getText())) {
					i.putExtra(ExceptionReportService.EXTRA_EXTRA_MESSAGE, text.getText().toString());
				}
				startService(i);
				dialog.dismiss();
				finish();
			}
		});
		dialog.setNegativeButton(getDialogNegativeButtonText(), new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
				finish();
			}
		});
		dialog.show();
	}
	
	private CharSequence getDialogPositiveButtonText() {
		int resId = DEFAULT_POSITIVE_BUTTON_TEXT;
		
		ApplicationInfo info = getPackageApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_DIALOG_SEND_BUTTON)) {
			resId = info.metaData.getInt(META_DATA_DIALOG_SEND_BUTTON);
		}
		return getText(resId);
	}
	
	private CharSequence getDialogNegativeButtonText() {
		int resId = DEFAULT_NEGATIVE_BUTTON_TEXT;
		
		ApplicationInfo info = getPackageApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_DIALOG_CANCEL_BUTTON)) {
			resId = info.metaData.getInt(META_DATA_DIALOG_CANCEL_BUTTON);
		}
		return getText(resId);
	}

	private CharSequence getDialogTitle() {
		CharSequence dialogTitle = DEFAULT_DIALOG_TITLE;
		
		PackageManager pm = getPackageManager();
		ApplicationInfo info = getPackageApplicationInfo();
		if (info != null) {
			if (info.metaData != null && info.metaData.containsKey(META_DATA_DIALOG_TITLE)) {
				dialogTitle = getText(info.metaData.getInt(META_DATA_DIALOG_TITLE));
			}
			return TextUtils.expandTemplate(dialogTitle, pm.getApplicationLabel(info));
		} else return dialogTitle;
	}
	
	private CharSequence getDialogText() {
		CharSequence dialogText = DEFAULT_DIALOG_TEXT;
		
		PackageManager pm = getPackageManager();
		ApplicationInfo info = getPackageApplicationInfo();
		if (info != null) {
			if (info.metaData != null && info.metaData.containsKey(META_DATA_DIALOG_TEXT)) {
				dialogText = getText(info.metaData.getInt(META_DATA_DIALOG_TEXT));
			}
			return TextUtils.expandTemplate(dialogText, pm.getApplicationLabel(info));
		} else return dialogText;
	}
	
	private CharSequence getDialogMessageHint() {
		CharSequence dialogMessageHint = null;
		
		ApplicationInfo info = getPackageApplicationInfo();
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_DIALOG_MESSAGE_HINT)) {
			dialogMessageHint = getText(info.metaData.getInt(META_DATA_DIALOG_MESSAGE_HINT));
		}
		return dialogMessageHint;
	}
	
	private Drawable getDialogIcon() {
		int dialogIcon = DEFAULT_DIALOG_ICON;
		
		if (info != null && info.metaData != null && info.metaData.containsKey(META_DATA_DIALOG_ICON)) {
			dialogIcon = info.metaData.getInt(META_DATA_DIALOG_ICON);
		}
		return getResources().getDrawable(dialogIcon);
	}
	
	private ApplicationInfo getPackageApplicationInfo() {
		if (info != null) return info;
		else {
			PackageManager pm = getPackageManager();
			ApplicationInfo info;
			try {
				info = pm.getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
				this.info = info;
				return info;
			} catch (NameNotFoundException e) {
				return null;
			}
		}
	}
	
}
