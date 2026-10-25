package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

final class WGramUi {
    interface TextResult { void accept(String text); }
    static void input(BaseFragment fragment, String title, String initial, boolean numeric, int max, TextResult result) {
        if (fragment.getParentActivity() == null) return;
        EditText edit = new EditText(fragment.getParentActivity());
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        edit.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        edit.setText(initial); edit.setSelection(edit.length());
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setTitle(title).setView(edit)
            .setPositiveButton(getString(R.string.OK), (d,w) -> result.accept(edit.getText().toString().trim()))
            .setNegativeButton(getString(R.string.Cancel), null).create());
    }
    static void info(BaseFragment fragment, String text) {
        if (fragment.getParentActivity() != null) fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle("WGram").setMessage(text).setPositiveButton(getString(R.string.OK),null).create());
    }
}
