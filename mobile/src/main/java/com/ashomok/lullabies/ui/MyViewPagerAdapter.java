package com.ashomok.lullabies.ui;

import android.app.Activity;
import android.app.DialogFragment;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.ashomok.lullabies.AlbumArtCache;
import com.ashomok.lullabies.R;
import com.ashomok.lullabies.utils.LogHelper;
import com.ashomok.lullabies.utils.rate_app.RateAppAskerCallback;
import com.ashomok.lullabies.utils.rate_app.RateAppAskerImpl;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.ashomok.lullabies.utils.MediaItemStateHelper.STATE_PLAYING;
import static com.ashomok.lullabies.utils.MediaItemStateHelper.getMediaItemState;
import static com.ashomok.lullabies.utils.MediaItemStateHelper.initializeColorStateLists;
import static com.ashomok.lullabies.utils.MediaItemStateHelper.sColorStateNotPlaying;
import static com.ashomok.lullabies.utils.MediaItemStateHelper.sColorStatePlaying;

public class MyViewPagerAdapter extends PagerAdapter implements RateAppAskerCallback {
    private static final String TAG = LogHelper.makeLogTag(MyViewPagerAdapter.class);
    private final RateAppAskerImpl rateAppAsker; //todo better move to another class (music service?)
    private Activity activity;

    //pager views by position
    private SparseArray<View> views;

    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     */
    private List<MediaBrowserCompat.MediaItem> mObjects;

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock.
     */
    private final Object mLock = new Object();


    @Inject
    public MyViewPagerAdapter(Activity activity, RateAppAskerImpl rateAppAsker) {
        this.activity = activity;
        this.rateAppAsker = rateAppAsker;
        views = new SparseArray<>();
        mObjects = new ArrayList<>();
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup collection, int position) {
        if (sColorStateNotPlaying == null || sColorStatePlaying == null) {
            initializeColorStateLists(activity);
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        ViewGroup convertView =
                (ViewGroup) inflater.inflate(R.layout.media_pager_item, collection, false);

        ImageView mBackgroundImage = convertView.findViewById(R.id.image);
        TextView textViewName = convertView.findViewById(R.id.name);
        TextView textViewGenre = convertView.findViewById(R.id.genre);

        MediaBrowserCompat.MediaItem mediaItem = mObjects.get(position);
        MediaDescriptionCompat description = mediaItem.getDescription();
        fetchImageAsync(description, mBackgroundImage);

        CharSequence name = description.getTitle();
        CharSequence category = description.getSubtitle();
        textViewName.setText(name);
        textViewGenre.setText(category);

        collection.addView(convertView);
        views.put(position, convertView);

        rateAppAsker.init(this);

        return convertView;
    }


    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object view) {
        container.removeView((View) view);
        views.remove(position);
    }

    @Override
    public int getCount() {
        return this.mObjects.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @Override
    public void notifyDataSetChanged() {
        try {
            int key;
            for (int i = 0; i < views.size(); i++) {
                key = views.keyAt(i);
                View view = views.get(key);

                MediaBrowserCompat.MediaItem mediaItem = mObjects.get(key);
                // If the state of convertView is different, we need to adapt the view to the new state.
                int state = getMediaItemState(activity, mediaItem);

                final ImageView tapMeImage = view.findViewById(R.id.tap_me_btn);
                if (state == STATE_PLAYING) {
                    tapMeImage.setVisibility(View.GONE);
                } else {
                    tapMeImage.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            //ignore
        }
        super.notifyDataSetChanged();
    }

    public void add(MediaBrowserCompat.MediaItem item) {
        synchronized (mLock) {
            mObjects.add(item);
        }
        notifyDataSetChanged();
    }

    /**
     * Remove all elements from the list.
     */
    public void clear() {
        synchronized (mLock) {
            mObjects.clear();
        }
        notifyDataSetChanged();
    }

    public MediaBrowserCompat.MediaItem getItem(int position) {
        return mObjects.get(position);
    }

    private void fetchImageAsync(
            @NonNull MediaDescriptionCompat description, ImageView mBackgroundImage) {
        if (description.getIconUri() == null) {
            return;
        }
        String artUrl = description.getIconUri().toString();
        AlbumArtCache cache = AlbumArtCache.getInstance();
        Bitmap art = cache.getBigImage(artUrl);
        if (art == null) {
            art = description.getIconBitmap();
        }
        if (art != null) {
            // if we have the art cached or from the MediaDescription, use it:
            mBackgroundImage.setImageBitmap(art);
        } else {
            // otherwise, fetch a high res version and update:

            //set placeholder
            ColorDrawable cd = new ColorDrawable(
                    activity.getResources().getColor(R.color.colorPrimarySecond));
            mBackgroundImage.setImageDrawable(cd);
            cache.fetch(artUrl, new AlbumArtCache.FetchListener() {

                @Override
                public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                    mBackgroundImage.setImageBitmap(bitmap);
                }
            });
        }
    }

    @Override
    public void showDialogFragment(DialogFragment dialogFragment) {
        if (activity != null) {
            dialogFragment.show(activity.getFragmentManager(), "dialog");
        }
    }
}