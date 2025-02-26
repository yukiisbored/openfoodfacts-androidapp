package openfoodfacts.github.scrachx.openfood.features;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class MessageDialogFragment extends DialogFragment {
    public interface MessageDialogListener {
        void onDialogPositiveClick(DialogFragment dialog);
    }

    private String mTitle;
    private String mMessage;
    private MessageDialogListener mListener;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setRetainInstance(true);
    }

    public static MessageDialogFragment newInstance(String title, String message, MessageDialogListener listener) {
        MessageDialogFragment fragment = new MessageDialogFragment();
        fragment.mTitle = title;
        fragment.mMessage = message;
        fragment.mListener = listener;
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setMessage(mMessage)
                .setTitle(mTitle)
                .setPositiveButton("OK", (dialog, id) -> {
                    if(mListener != null) {
                        mListener.onDialogPositiveClick(MessageDialogFragment.this);
                    }
                }).create();
    }
}
