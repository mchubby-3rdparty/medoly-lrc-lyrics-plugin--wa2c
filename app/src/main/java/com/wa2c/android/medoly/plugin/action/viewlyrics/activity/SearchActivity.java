package com.wa2c.android.medoly.plugin.action.viewlyrics.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;
import com.wa2c.android.medoly.plugin.action.viewlyrics.R;
import com.wa2c.android.medoly.plugin.action.viewlyrics.db.SearchCacheHelper;
import com.wa2c.android.medoly.plugin.action.viewlyrics.dialog.ConfirmDialogFragment;
import com.wa2c.android.medoly.plugin.action.viewlyrics.search.Result;
import com.wa2c.android.medoly.plugin.action.viewlyrics.search.ResultItem;
import com.wa2c.android.medoly.plugin.action.viewlyrics.search.ViewLyricsSearcher;
import com.wa2c.android.medoly.plugin.action.viewlyrics.util.AppUtils;
import com.wa2c.android.medoly.plugin.action.viewlyrics.util.Logger;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * Search Activity.
 */
@EActivity(R.layout.activity_search)
@OptionsMenu(R.menu.activity_search)
public class SearchActivity extends Activity {

    private static final int REQUEST_CODE_SAVE_FILE = 1;
    public static final String INTENT_SEARCH_TITLE = "INTENT_SEARCH_TITLE";
    public static final String INTENT_SEARCH_ARTIST = "INTENT_SEARCH_ARTIST";

    /** Search list adapter. */
    private SearchResultAdapter searchResultAdapter;
    /** Search cache helper. */
    private SearchCacheHelper searchCacheHelper;
    /** Selected ResultItem object. */
    private ResultItem currentResultItem;

    /** Language names. */
    private String[] languageNames;
    /** Language profiles. */
    private String[] languageProfiles;

    @Extra(INTENT_SEARCH_TITLE)
    String intentSearchTitle;
    @Extra(INTENT_SEARCH_ARTIST)
    String intentSearchArtist;

    /**
     * Get language names.
     * @return language names.
     */
    private synchronized String[] getLanguageNames() {
        if (this.languageNames == null)
            this.languageNames = getResources().getStringArray(R.array.language_names);
        return this.languageNames;
    }

    /**
     * Get language profiles.
     * @return language profiles.
     */
    private synchronized  String[] getLanguageProfiels() {
        if (this.languageProfiles == null)
            this.languageProfiles = getResources().getStringArray(R.array.language_profiles);
        return this.languageProfiles;
    }


    @ViewById
    Button searchTitleButton;
    @ViewById
    EditText searchTitleEditText;
    @ViewById
    Button searchArtistButton;
    @ViewById
    EditText searchArtistEditText;
    @ViewById
    ImageButton searchClearButton;
    @ViewById
    ImageButton searchStartButton;
    @ViewById
    ListView searchResultListView;
    @ViewById
    ScrollView searchLyricsScrollView;
    @ViewById
    TextView searchLyricsTextView;

    @ViewById
    View searchResultLoadingLayout;
    @ViewById
    View searchLyricsLoadingLayout;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @AfterViews
    void afterViews() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        searchCacheHelper = new SearchCacheHelper(this);
        searchResultAdapter = new SearchResultAdapter(this);
        searchResultListView.setAdapter(searchResultAdapter);

        searchTitleEditText.setText(intentSearchTitle);
        searchArtistEditText.setText(intentSearchArtist);
    }

    @OptionsItem(android.R.id.home)
    void menuHomeClick() {
        startActivity(new Intent(this, MainActivity_.class));
    }

    @OptionsItem(R.id.menu_search_save_file)
    void menuSaveFileClick() {
        if (!existsLyrics()) {
            AppUtils.showToast(this, R.string.error_exists_lyrics);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, currentResultItem.getMusicTitle() + ".lrc");
        startActivityForResult(intent, REQUEST_CODE_SAVE_FILE);
    }

    @OptionsItem(R.id.menu_search_save_cache)
    void menuSaveCacheClick() {
        if (!existsLyrics()) {
            AppUtils.showToast(this, R.string.error_exists_lyrics);
            return;
        }

        ConfirmDialogFragment dialog = ConfirmDialogFragment.newInstance(
                getString(R.string.dialog_confirm_message_save_cache),
                getString(R.string.label_confirmation),
                getString(R.string.dialog_confirm_label_save_cache),
                null,
                getString(android.R.string.cancel)
        );
        dialog.setClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    String title = searchTitleEditText.getText().toString();
                    String artist = searchArtistEditText.getText().toString();
                    saveBackground(title, artist, currentResultItem);
                }
            }
        });
        dialog.show(this);
    }

    @OptionsItem(R.id.menu_search_open_cache)
    void menuOpenCacheClick() {
        Intent intent = new Intent(this, CacheActivity_.class);
        intent.putExtra(CacheActivity.INTENT_SEARCH_TITLE, searchTitleEditText.getText().toString());
        intent.putExtra(CacheActivity.INTENT_SEARCH_ARTIST, searchArtistEditText.getText().toString());
        startActivity(intent);
    }

    @Background
    void saveBackground(String title, String artist, ResultItem item) {
        Locale locale = Locale.getDefault();
        try {
            // Language profile
            if (DetectorFactory.getLangList() == null || DetectorFactory.getLangList().size() == 0) {
                // initialize
                DetectorFactory.clear();
                DetectorFactory.loadProfile(Arrays.asList(getLanguageProfiels()));
            }
            Detector detector = DetectorFactory.create();
            detector.append(item.getLyrics());
            ArrayList<Language> languages = detector.getProbabilities();
            if (languages != null || languages.size() != 0)
                locale = new Locale(languages.get(0).lang);
        } catch (LangDetectException e) {
            Logger.e(e);
        }

        if (searchCacheHelper.insertOrUpdate(title, artist, locale, item))
            AppUtils.showToast(this, R.string.message_save_cache);
    }

    @Click(R.id.searchTitleButton)
    void searchTitleButtonClick(View view) {

    }

    @Click(R.id.searchArtistButton)
    void searchArtistButtonClick(View view) {

    }

    @Click(R.id.searchClearButton)
    void searchClearButtonClick(View view) {
        searchTitleEditText.setText(null);
        searchArtistEditText.setText(null);
    }

    @Click(R.id.searchStartButton)
    void searchStartButtonClick(View view) {
        // Hide keyboard
        InputMethodManager inputMethodMgr = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodMgr.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        String title = searchTitleEditText.getText().toString();
        String artist = searchArtistEditText.getText().toString();
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(artist)) {
            AppUtils.showToast(this, R.string.error_input_condition);
            return;
        }

        // Clear view
        showSearchResult(null);
        showLyrics(null);
        currentResultItem = null;

        searchResultListView.setVisibility(View.INVISIBLE);
        searchResultLoadingLayout.setVisibility(View.VISIBLE);
        searchLyrics(title, artist);
    }

    // Search

    @Background
    void searchLyrics(String title, String artist) {
        Result result = null;
        try {
            result = ViewLyricsSearcher.search(title, artist, 0);
        } catch (Exception e) {
            Logger.e(e);
        } finally {
            showSearchResult(result);
        }
    }

    @UiThread
    void showSearchResult(Result result) {
        try {
            searchResultAdapter.clear();
            if (result != null)
                searchResultAdapter.addAll(result.getInfoList());
            searchResultAdapter.notifyDataSetChanged();
        } finally {
            searchResultListView.setVisibility(View.VISIBLE);
            searchResultLoadingLayout.setVisibility(View.INVISIBLE);
        }
    }

    // Download

    @ItemClick(R.id.searchResultListView)
    void searchResultListViewItemClick(@NonNull ResultItem item) {
        // Hide keyboard
        InputMethodManager inputMethodMgr = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodMgr.hideSoftInputFromWindow(searchResultListView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        // Clear view
        showLyrics(null);

        searchLyricsScrollView.setVisibility(View.INVISIBLE);
        searchLyricsLoadingLayout.setVisibility(View.VISIBLE);
        downloadLyrics(item);
    }

    @Background
    void downloadLyrics(ResultItem item) {
        try {
            if (item != null) {
                String lyrics = ViewLyricsSearcher.download(item.getLyricURL());
                item.setLyrics(lyrics);
            }
        } catch (Exception e) {
            Logger.e(e);
        } finally {
            showLyrics(item);
        }
    }

    @UiThread
    void showLyrics(ResultItem item) {
        if (item == null) {
            searchLyricsTextView.setText(null);
        } else {
            searchLyricsTextView.setText(item.getLyrics());
        }
        currentResultItem = item;
        searchLyricsScrollView.setVisibility(View.VISIBLE);
        searchLyricsLoadingLayout.setVisibility(View.INVISIBLE);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_CODE_SAVE_FILE) {
            // 歌詞のファイル保存
            if (resultCode == RESULT_OK) {
                if (!existsLyrics()) {
                    AppUtils.showToast(this, R.string.error_exists_lyrics);
                    return;
                }

                Uri uri = resultData.getData();
                try (OutputStream stream = getContentResolver().openOutputStream(uri);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream,  "UTF-8"))) {
                    writer.write(currentResultItem.getLyrics());
                    writer.flush();
                    AppUtils.showToast(this, R.string.message_lyrics_save_succeeded);
                } catch (IOException e) {
                    Logger.e(e);
                    AppUtils.showToast(this, R.string.message_lyrics_save_failed);
                }
            }
        }

        // Hide keyboard
        InputMethodManager inputMethodMgr = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodMgr.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    /**
     * Check existence of lyrics
     * @return true if exists lyrics.
     */
    private synchronized boolean existsLyrics() {
        return (currentResultItem != null) && !TextUtils.isEmpty(currentResultItem.getLyricURL());
    }

    /**
     * Search result adapter.
     */
    private static class SearchResultAdapter extends ArrayAdapter<ResultItem> {

        SearchResultAdapter(Context context) {
            super(context, R.layout.layout_search_item);
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull final ViewGroup parent) {
            // ビュー作成
            final ListItemViewHolder holder;
            if (convertView == null) {
                final View view = View.inflate(parent.getContext(), R.layout.layout_search_item, null);
                holder = new ListItemViewHolder();
                holder.searchItemTitleTextView  = (TextView)view.findViewById(R.id.searchItemTitleTextView);
                holder.searchItemArtistTextView = (TextView)view.findViewById(R.id.searchItemArtistTextView);
                holder.searchItemAlbumTextView  = (TextView)view.findViewById(R.id.searchItemAlbumTextView);
                holder.searchItemDownloadTextView = (TextView)view.findViewById(R.id.searchItemDownloadTextView);
                holder.searchItemRatingTextView = (TextView)view.findViewById(R.id.searchItemRatingTextView);
                holder.searchItemFromTextView   = (TextView)view.findViewById(R.id.searchItemFromTextView);
                view.setTag(holder);
                convertView = view;
            } else {
                holder = (ListItemViewHolder) convertView.getTag();
            }

            // データ設定
            ResultItem item = getItem(position);
            if (item == null)
                item = new ResultItem();
            holder.searchItemTitleTextView.setText(item.getMusicTitle());
            holder.searchItemArtistTextView.setText(AppUtils.nvl(item.getMusicArtist(), "-"));
            holder.searchItemAlbumTextView.setText(AppUtils.nvl(item.getMusicAlbum(), "-"));
            holder.searchItemDownloadTextView.setText(getContext().getString(R.string.label_search_item_download, item.getLyricDownloadsCount()));
            holder.searchItemRatingTextView.setText(getContext().getString(R.string.label_search_item_rating, item.getLyricRatesCount()));
            holder.searchItemFromTextView.setText(getContext().getString(R.string.label_search_item_from, item.getLyricUploader()));

            return convertView;
        }

    }

    /** リスト項目のビュー情報を保持するHolder。 */
    private static class ListItemViewHolder {
        TextView searchItemTitleTextView;
        TextView searchItemArtistTextView;
        TextView searchItemAlbumTextView;
        TextView searchItemDownloadTextView;
        TextView searchItemRatingTextView;
        TextView searchItemFromTextView;
    }

}
