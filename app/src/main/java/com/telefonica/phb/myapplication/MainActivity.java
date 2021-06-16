package com.telefonica.phb.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.mediarouter.app.MediaRouteActionProvider;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaItemStatus;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaSessionStatus;
import androidx.mediarouter.media.RemotePlaybackClient;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.LaunchOptions;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private final Context context = this;

    private MediaRouter mediaRouter;
    private MediaRouteSelector mSelector;

    // Variables to hold the currently selected route and its playback client
    private MediaRouter.RouteInfo mRoute;
    private RemotePlaybackClient remotePlaybackClient;

    private CastDevice mSelectedDevice;
    private Cast.Listener mCastClientListener;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private GoogleApiClient mApiClient;

    // Define the Callback object and its methods, save the object in a class variable
    private final MediaRouter.Callback mediaRouterCallback =
            new MediaRouter.Callback() {

                @Override
                public void onRouteSelected(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route, int reason) {
                    super.onRouteSelected(router, route, reason);

                    Log.d("TAG", "onRouteSelected: route=" + route);

                    // Save the new route
                    mRoute = route;

                    mSelectedDevice = CastDevice.getFromBundle(route.getExtras());
                    mCastClientListener = new Cast.Listener() {
                        @Override
                        public void onApplicationStatusChanged() {
                            Log.i("MediaRouter", "Cast.Listener.onApplicationStatusChanged()");
                        }

                        @Override
                        public void onApplicationMetadataChanged(ApplicationMetadata applicationMetadata) {
                            Log.i("MediaRouter", "Cast.Listener.onApplicationMetadataChanged(" + applicationMetadata + ")");

                            if (applicationMetadata != null) {
                                LaunchOptions launchOptions = new LaunchOptions.Builder().setRelaunchIfRunning(false).build();
                                Cast.CastApi.launchApplication(mApiClient, applicationMetadata.getApplicationId(), launchOptions).setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
                                    @Override
                                    public void onResult(@NonNull Cast.ApplicationConnectionResult applicationConnectionResult) {
                                        Log.i("MediaRouter", "Cast.CastApi.joinApplication.onResult() " + applicationConnectionResult.getSessionId());

                                        // Attach a new playback client
                                        remotePlaybackClient = new RemotePlaybackClient(context, mRoute);
                                        remotePlaybackClient.setStatusCallback(new RemotePlaybackClient.StatusCallback() {
                                            @Override
                                            public void onItemStatusChanged(Bundle data, String sessionId, MediaSessionStatus sessionStatus, String itemId, MediaItemStatus itemStatus) {
                                                super.onItemStatusChanged(data, sessionId, sessionStatus, itemId, itemStatus);
                                            }

                                            @Override
                                            public void onSessionStatusChanged(Bundle data, String sessionId, MediaSessionStatus sessionStatus) {
                                                super.onSessionStatusChanged(data, sessionId, sessionStatus);
                                            }

                                            @Override
                                            public void onSessionChanged(String sessionId) {
                                                super.onSessionChanged(sessionId);
                                            }
                                        });
                                        //remotePlaybackClient.getSessionStatus(null, null);
                                        
                                        mRemoteMediaPlayer = new RemoteMediaPlayer();
                                        mRemoteMediaPlayer.setOnStatusUpdatedListener( new RemoteMediaPlayer.OnStatusUpdatedListener() {
                                            @Override
                                            public void onStatusUpdated() {
                                                MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                                                MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();
                                                Log.i("MediaRouter", "Remote media player content video ID " + mediaInfo.getContentId());
                                                Log.i("MediaRouter", "Remote media player content video title " + mediaInfo.getMetadata().getString(MediaMetadata.KEY_TITLE));
                                                Log.i("MediaRouter", "Remote media player content media duration in seconds " + mediaInfo.getStreamDuration() / 1000);
                                                Log.i("MediaRouter", "Remote media player content media position (second) " + mediaStatus.getStreamPosition() / 1000);
                                                // TODO: you can call isChromecastPlaying() now
                                            }
                                        });

                                        try {
                                            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
                                        } catch(IOException e) {
                                            Log.e("MediaRouter", "Exception while creating media channel ", e );
                                        } catch(NullPointerException e) {
                                            Log.e("MediaRouter", "Something wasn't reinitialized for reconnectChannels", e);
                                        }


                                        mRemoteMediaPlayer.requestStatus(mApiClient).setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                                            @Override
                                            public void onResult(@NonNull RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                                                Log.i("MediaRouter", "requestStatus() " + mediaChannelResult);
                                            }
                                        });

                                        try {
                                            Cast.CastApi.requestStatus(mApiClient);
                                        } catch (IOException e) {
                                            Log.e("MediaRouter", "Couldn't request status", e);
                                        }
                                    }
                                });
                            }
                        }

                        @Override
                        public void onApplicationDisconnected(int i) {
                            Log.i("MediaRouter", "Cast.Listener.onApplicationDisconnected(" + i + ")");
                        }

                        @Override
                        public void onActiveInputStateChanged(int i) {
                            Log.i("MediaRouter", "Cast.Listener.onActiveInputStateChanged(" + i + ")");
                        }

                        @Override
                        public void onStandbyStateChanged(int i) {
                            Log.i("MediaRouter", "Cast.Listener.onStandbyStateChanged(" + i + ")");
                        }

                        @Override
                        public void onVolumeChanged() {
                            Log.i("MediaRouter", "Cast.Listener.onVolumeChanged()");
                        }
                    };

                    Cast.CastOptions.Builder apiOptionsBuilder = new Cast.CastOptions.Builder(mSelectedDevice, mCastClientListener);

                    mApiClient = new GoogleApiClient.Builder(context)
                            .addApi( Cast.API, apiOptionsBuilder.build() )
                            .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                                @Override
                                public void onConnected(@Nullable Bundle bundle) {
                                    Log.i("MediaRouter", "GoogleApiClient.onConnected()");
                                    Log.i("MediaRouter", "Bundle " + bundle);
                                }

                                @Override
                                public void onConnectionSuspended(int i) {
                                    Log.i("MediaRouter", "GoogleApiClient.onConnectionSuspended(" + i + ")");
                                }
                            })
                            .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                                @Override
                                public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                                    Log.i("MediaRouter", "GoogleApiClient.onConnectionFailed()");
                                }
                            })
                            .build();

                    mApiClient.connect();
                }

                @Override
                public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route, int reason) {
                    Log.d("TAG", "onRouteUnselected: route=" + route);

                    if (route.supportsControlCategory(
                            MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {

                        // Changed route: tear down previous client
                        if (mRoute != null && remotePlaybackClient != null) {
                            remotePlaybackClient.release();
                            remotePlaybackClient = null;
                        }

                        // Save the new route
                        mRoute = route;

                        if (reason != MediaRouter.UNSELECT_REASON_ROUTE_CHANGED) {
                            // Resume local playback  (if necessary)
                            // ...
                        }
                    }
                }
            };

    // Use this callback to run your MediaRouteSelector to generate the list of available media routes
    @Override
    public void onStart() {
        mediaRouter.addCallback(mSelector, mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        super.onStart();
    }

    // Remove the selector on stop to tell the media router that it no longer
    // needs to discover routes for your app.
    @Override
    public void onStop() {
        mediaRouter.removeCallback(mediaRouterCallback);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the media router service.
        mediaRouter = MediaRouter.getInstance(this);

        // Create a route selector for the type of routes your app supports.
        mSelector = new MediaRouteSelector.Builder()
                // These are the framework-supported intents
                //.addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                //.addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // Inflate the menu and configure the media router action provider.
        getMenuInflater().inflate(R.menu.menu, menu);

        // Attach the MediaRouteSelector to the menu item
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(
                        mediaRouteMenuItem);
        // Attach the MediaRouteSelector that you built in onCreate()
        mediaRouteActionProvider.setRouteSelector(mSelector);

        // Return true to show the menu.
        return true;
    }
}