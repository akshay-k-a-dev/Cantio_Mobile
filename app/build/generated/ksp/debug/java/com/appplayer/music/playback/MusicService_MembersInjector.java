package com.appplayer.music.playback;

import androidx.media3.datasource.cache.SimpleCache;
import com.appplayer.music.di.DownloadCache;
import com.appplayer.music.di.PlayerCache;
import com.appplayer.music.utils.YTPlayerUtils;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@QualifierMetadata({
    "com.appplayer.music.di.PlayerCache",
    "com.appplayer.music.di.DownloadCache"
})
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class MusicService_MembersInjector implements MembersInjector<MusicService> {
  private final Provider<SimpleCache> playerCacheProvider;

  private final Provider<SimpleCache> downloadCacheProvider;

  private final Provider<YTPlayerUtils> ytPlayerUtilsProvider;

  public MusicService_MembersInjector(Provider<SimpleCache> playerCacheProvider,
      Provider<SimpleCache> downloadCacheProvider, Provider<YTPlayerUtils> ytPlayerUtilsProvider) {
    this.playerCacheProvider = playerCacheProvider;
    this.downloadCacheProvider = downloadCacheProvider;
    this.ytPlayerUtilsProvider = ytPlayerUtilsProvider;
  }

  public static MembersInjector<MusicService> create(Provider<SimpleCache> playerCacheProvider,
      Provider<SimpleCache> downloadCacheProvider, Provider<YTPlayerUtils> ytPlayerUtilsProvider) {
    return new MusicService_MembersInjector(playerCacheProvider, downloadCacheProvider, ytPlayerUtilsProvider);
  }

  @Override
  public void injectMembers(MusicService instance) {
    injectPlayerCache(instance, playerCacheProvider.get());
    injectDownloadCache(instance, downloadCacheProvider.get());
    injectYtPlayerUtils(instance, ytPlayerUtilsProvider.get());
  }

  @InjectedFieldSignature("com.appplayer.music.playback.MusicService.playerCache")
  @PlayerCache
  public static void injectPlayerCache(MusicService instance, SimpleCache playerCache) {
    instance.playerCache = playerCache;
  }

  @InjectedFieldSignature("com.appplayer.music.playback.MusicService.downloadCache")
  @DownloadCache
  public static void injectDownloadCache(MusicService instance, SimpleCache downloadCache) {
    instance.downloadCache = downloadCache;
  }

  @InjectedFieldSignature("com.appplayer.music.playback.MusicService.ytPlayerUtils")
  public static void injectYtPlayerUtils(MusicService instance, YTPlayerUtils ytPlayerUtils) {
    instance.ytPlayerUtils = ytPlayerUtils;
  }
}
