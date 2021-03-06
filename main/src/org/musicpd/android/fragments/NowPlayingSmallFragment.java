package org.musicpd.android.fragments;

import org.a0z.mpd.MPD;
import org.a0z.mpd.MPDStatus;
import org.a0z.mpd.Music;
import org.a0z.mpd.event.StatusChangeListener;
import org.a0z.mpd.exception.MPDServerException;

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import org.musicpd.android.MPDApplication;
import org.musicpd.android.R;
import org.musicpd.android.cover.CoverBitmapDrawable;
import org.musicpd.android.helpers.CoverAsyncHelper;
import org.musicpd.android.helpers.AlbumCoverDownloadListener;
import org.musicpd.android.tools.Job;
import org.musicpd.android.tools.Log;

public class NowPlayingSmallFragment extends SherlockFragment implements StatusChangeListener {

	private MPDApplication app;
	private CoverAsyncHelper coverHelper;
	private TextView songTitle;
	private TextView songArtist;

	private AlbumCoverDownloadListener coverArtListener;
	private ImageView coverArt;
	private ProgressBar coverArtProgress;

	private ImageButton buttonPrev;
	private ImageButton buttonPlayPause;
	private ImageButton buttonNext;
	private String lastArtist = "";
	private String lastAlbum = "";

	private static final int FALLBACK_COVER_SIZE = 48; // In DIP

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		app = (MPDApplication) activity.getApplication();
	}

	@Override
	public void onStart() {
		super.onStart();
		app.oMPDAsyncHelper.addStatusChangeListener(this);
		updateTrackInfo();
	}

	@Override
	public void onStop() {
		app.oMPDAsyncHelper.removeStatusChangeListener(this);
		super.onStop();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.now_playing_small_fragment, container, false);
		songTitle = (TextView) view.findViewById(R.id.song_title);
		songArtist = (TextView) view.findViewById(R.id.song_artist);
		buttonPrev = (ImageButton) view.findViewById(R.id.prev);
		buttonPlayPause = (ImageButton) view.findViewById(R.id.playpause);
		buttonNext = (ImageButton) view.findViewById(R.id.next);
		buttonPrev.setOnClickListener(buttonClickListener);
		buttonPlayPause.setOnClickListener(buttonClickListener);
		buttonNext.setOnClickListener(buttonClickListener);

		coverArt = (ImageView) view.findViewById(R.id.albumCover);
		coverArtProgress = (ProgressBar) view.findViewById(R.id.albumCoverProgress);
		coverArtListener = new AlbumCoverDownloadListener(getActivity(), coverArt, coverArtProgress);

		coverHelper = new CoverAsyncHelper(app, PreferenceManager.getDefaultSharedPreferences(getActivity()));
		coverHelper.setCoverRetrieversFromPreferences();
		coverHelper.setCoverMaxSizeFromScreen(getActivity());
		coverHelper.setCachedCoverMaxSize(coverArt.getHeight());
		coverHelper.addCoverDownloadListener(coverArtListener);

		return view;
	}

	@Override
	public void onDestroyView() {
		if (coverArt != null) {
			final Drawable oldDrawable = coverArt.getDrawable();
			coverArt.setImageResource(R.drawable.no_cover_art);
			if (oldDrawable != null && oldDrawable instanceof CoverBitmapDrawable) {
				final Bitmap oldBitmap = ((CoverBitmapDrawable) oldDrawable).getBitmap();
				if (oldBitmap != null)
					oldBitmap.recycle();
			}
		}
		super.onDestroyView();
	}

	final OnClickListener buttonClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
				case R.id.prev:
					new Job() {
						@Override
						public void run() {
							try {
								app.oMPDAsyncHelper.oMPD.previous();
							} catch (MPDServerException e) {
								Log.w(e.getMessage());
							}
						}
					};
					break;
				case R.id.playpause:
					new Job() {
						@Override
						public void run() {
							try {
								final MPD mpd = app.oMPDAsyncHelper.oMPD;
								final String state = mpd.getStatus().getState();
								if (state.equals(MPDStatus.MPD_STATE_PLAYING) || state.equals(MPDStatus.MPD_STATE_PAUSED)) {
									mpd.pause();
								} else {
									mpd.play();
								}
							} catch (MPDServerException e) {
								Log.w(e.getMessage());
							}
						}
					};
					break;
				case R.id.next:
					new Job() {
						@Override
						public void run() {
							try {
								app.oMPDAsyncHelper.oMPD.next();
							} catch (MPDServerException e) {
								Log.w(e.getMessage());
							}
						}
					};
					break;
			}
		}
	};
	
	@Override
	public void trackChanged(MPDStatus mpdStatus, int oldTrack) {
		updateTrackInfo(mpdStatus);
	}

	@Override
	public void playlistChanged(MPDStatus mpdStatus, int oldPlaylistVersion) {
		if (isDetached())
			return;
		// If the playlist changed but not the song position in the playlist
		// We end up being desynced. Update the current song.
		updateTrackInfo();
	}

	@Override
	public void stateChanged(MPDStatus status, String oldState) {
		if (isDetached())
			return;
		app.getApplicationState().currentMpdStatus = status;
		if (status.getState() != null && buttonPlayPause != null) {
			if (status.getState().equals(MPDStatus.MPD_STATE_PLAYING)) {
				buttonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_media_pause));
			} else {
				buttonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_media_play));
			}
		}
	}

	@Override
	public void connectionStateChanged(boolean connected, boolean connectionLost) {
		if (isDetached() || songTitle == null || songArtist == null)
			return;
		connected = ((MPDApplication) getActivity().getApplication()).oMPDAsyncHelper.oMPD.isConnected();
		if (connected) {
			songTitle.setText(getResources().getString(R.string.noSongInfo));
			songArtist.setText("");
		} else {
			songTitle.setText(getResources().getString(R.string.notConnected));
			songArtist.setText("");
		}
		return;
	}
	
	public void updateTrackInfo() {
		updateTrackInfo(null);
	}

	public void updateTrackInfo(MPDStatus status) {
		new updateTrackInfoAsync(((MPDApplication) getActivity().getApplication()).oMPDAsyncHelper.oMPD).execute(status);
	}

	public class updateTrackInfoAsync extends AsyncTask<MPDStatus, Void, Boolean> {
		Music actSong = null;
		MPDStatus status = null;
		final MPD oMPD;
		public updateTrackInfoAsync(MPD oMPD) { this.oMPD = oMPD; }

		@Override
		protected Boolean doInBackground(MPDStatus... params) {
			String state = null;
			try {
				if (params == null || params[0] == null)
					state = (status = oMPD.getStatus()).getState();
				else
					state = (status = params[0]).getState();
				if (state != null) {
					int songPos = status.getSongPos();
					if (songPos >= 0) {
						actSong = oMPD.getPlaylist().getByIndex(songPos);
						return true;
					}
				}
			} catch (Exception e) {
				Log.w(e);
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result != null && result) {
				String artist = null;
				String title = null;
				String album = null;
				boolean noSong = actSong == null || status.getPlaylistLength() == 0;
				if (noSong) {
					title = getResources().getString(R.string.noSongInfo);
				} else {
					artist = actSong.getArtist();
					title = actSong.getTitle();
					album = actSong.getAlbum();
				}

				artist = artist == null ? "" : artist;
				title = title == null ? "" : title;
				album = album == null ? "" : album;

				songArtist.setText(artist);
				songTitle.setText(title);
				if (noSong || actSong.isStream()) {
					lastArtist = artist;
					lastAlbum = album;
					coverArtListener.onCoverNotFound();
				} else if (!lastAlbum.equals(album) || !lastArtist.equals(artist)) {
					coverArtProgress.setVisibility(ProgressBar.VISIBLE);
					coverHelper.downloadCover(artist, album, actSong.getPath(), actSong.getFilename());
					lastArtist = artist;
					lastAlbum = album;
				}
				try {
					stateChanged(status, null);
				} catch(Exception e) {
					Log.w(e);
				}
			} else {
				songArtist.setText("");
				songTitle.setText(R.string.noSongInfo);
			}
		}
	}

	/*****************************
	 * Stuff we don't care about *
	 ****************************/

	@Override
	public void volumeChanged(MPDStatus mpdStatus, int oldVolume) {
	}

	@Override
	public void repeatChanged(boolean repeating) {
	}

	@Override
	public void randomChanged(boolean random) {

	}

	@Override
	public void libraryStateChanged(boolean updating) {
	}

}
