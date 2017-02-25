package com.wa2c.android.medoly.plugin.action.lrclyrics.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.wa2c.android.medoly.plugin.action.lrclyrics.R;
import com.wa2c.android.medoly.plugin.action.lrclyrics.activity.SearchActivity;
import com.wa2c.android.medoly.plugin.action.lrclyrics.activity.SearchActivity_;
import com.wa2c.android.medoly.plugin.action.lrclyrics.db.SearchCache;
import com.wa2c.android.medoly.plugin.action.lrclyrics.search.ResultItem;


/**
 * Confirm dialog.
 */
public class CacheDialogFragment extends AbstractDialogFragment {

    /** Cache key. */
    private final static String ARG_CACHE = "ARG_CACHE";

    /**
     * Create dialog instance.
     * @param cache Search cache.
     * @return Dialog instance.
     */
    static public CacheDialogFragment newInstance(SearchCache cache) {
        CacheDialogFragment fragment = new CacheDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CACHE, cache);
        fragment.setArguments(args);

        return fragment;
    }



    /**
     * onCreateDialog
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        // data
        final SearchCache cache = (SearchCache)getArguments().getSerializable(ARG_CACHE);
        ResultItem result = cache.getResultItem();

        // view
        final View content = View.inflate(getActivity(), R.layout.dialog_cache, null);
        TextView textView = (TextView)content.findViewById(R.id.dialogCacheLyricsTextView);
        textView.setText(result != null ? result.getLyrics() : getString(R.string.message_dialog_cache_none));

        // build dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.title_activity_cache);
        builder.setView(content);
        builder.setNegativeButton(R.string.label_close, clickListener);
        if (result != null) {
            builder.setPositiveButton(R.string.menu_search_save_file, clickListener);
            builder.setNeutralButton(R.string.label_dialog_cache_research, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(getActivity(), SearchActivity_.class);
                    intent.putExtra(SearchActivity.INTENT_SEARCH_TITLE, cache.title);
                    intent.putExtra(SearchActivity.INTENT_SEARCH_ARTIST, cache.artist);
                    startActivity(intent);
                }
            });
        }
        return  builder.create();
    }

}