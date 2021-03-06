package com.wa2c.android.medoly.plugin.action.lrclyrics.db;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.wa2c.android.medoly.plugin.action.lrclyrics.search.ResultItem;
import com.wa2c.android.medoly.plugin.action.lrclyrics.util.AppUtils;

import java.util.Collection;
import java.util.List;

/**
 * Search cache adapter.
 */

public class SearchCacheHelper {

    /** Singleton object. */
    private static OrmaDatabase ormaDatabase = null;

    /**
     * Povide OrmaDatabase.
     * @param context context.
     * @return OrmaDatabase object.
     */
    public static synchronized OrmaDatabase provideOrmaDatabase(Context context) {
        if (ormaDatabase == null) {
            ormaDatabase = OrmaDatabase.builder(context).build();
        }
        return ormaDatabase;
    }



    /** Context. */
    private Context context;
    /** Gson. */
    private Gson gson;

    /**
     * Constructor.
     * @param context Context.
     */
    public SearchCacheHelper(@NonNull Context context) {
        this.context = context;
        this.gson = new Gson();
    }


    /**
     * Get ResultItem object from title and artist.
     * @param title Search title.
     * @param artist Search artist.
     * @return Exists item, null if not exists.
     */
    public SearchCache select(String title, String artist) {
        OrmaDatabase od = provideOrmaDatabase(context);
        return od.selectFromSearchCache()
                .titleAndArtistEq(AppUtils.coalesce(title), AppUtils.coalesce(artist))
                .valueOrNull();
    }

    /**
     * Get Search Cache object matched on title and artist.
     * @param title Search title.
     * @param artist Search artist.
     * @return Exists item, null if not exists.
     */
    public List<SearchCache> search(String title, String artist) {
        OrmaDatabase od = provideOrmaDatabase(context);
        SearchCache_Selector selector = od.selectFromSearchCache();
        // title
        if (!TextUtils.isEmpty(title))
            selector.where("title like ?", "%" + title + "%");
        // artist
        if (!TextUtils.isEmpty(artist))
            selector.where("artist like ?", "%" + artist + "%");

        return selector.orderBytitleAndArtistAsc()
                .toList();
    }

    /**
     * Insert or update cache.
     * @param title Search title.
     * @param artist Search artist.
     * @param resultItem Result item.
     * @return true as succeeded.
     */
    public boolean insertOrUpdate(@NonNull String title, String artist, ResultItem resultItem) {
        OrmaDatabase od = provideOrmaDatabase(context);

        String language = null;
        String from = null;
        String file_name = null;
        Boolean has_lyrics = false;
        String result = gson.toJson(resultItem);
        if (resultItem != null) {
            language = resultItem.getLanguage();
            from = resultItem.getLyricUploader();
            file_name = resultItem.getLyricURL().substring(resultItem.getLyricURL().lastIndexOf("/") + 1).replace(".lrc", "");
            has_lyrics = (resultItem.getLyrics() != null);
        }

        title = AppUtils.coalesce(title);
        artist = AppUtils.coalesce(artist);

        SearchCache cache = select(title, artist);
        if (cache != null) {
            int count = od.updateSearchCache()._idEq(cache._id)
                    .language(language)
                    .from(from)
                    .file_name(file_name)
                    .has_lyrics(has_lyrics)
                    .result(result)
                    .execute();
            return (count > 0);
        } else {
            // Insert model
            cache = new SearchCache();
            cache.title = title;
            cache.artist = artist;
            cache.language = language;
            cache.from = from;
            cache.file_name =  file_name;
            cache.has_lyrics = has_lyrics;
            cache.result = result;
            long id = od.insertIntoSearchCache(cache);
            return (id >= 0);
        }
    }

    /**
     * Delete cache.
     * @param caches Cache.
     * @return true as succeeded.
     */
    public boolean delete(final Collection<SearchCache> caches) {
        if (caches == null || caches.size() == 0)
            return false;

        final OrmaDatabase od = provideOrmaDatabase(context);

        od.transactionSync(new Runnable() {
            @Override
            public void run() {
                for (SearchCache c : caches) {
                    od.deleteFromSearchCache()
                            ._idEq(c._id)
                            .execute();
                }
            }
        });

        return true;
    }






}
