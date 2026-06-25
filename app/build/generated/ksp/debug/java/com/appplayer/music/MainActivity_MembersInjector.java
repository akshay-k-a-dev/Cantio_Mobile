package com.appplayer.music;

import com.appplayer.music.playback.PlayerConnection;
import com.appplayer.music.utils.TokenManager;
import com.appplayer.music.utils.YTPlayerUtils;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@QualifierMetadata
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<PlayerConnection> playerConnectionProvider;

  private final Provider<TokenManager> tokenManagerProvider;

  private final Provider<YTPlayerUtils> ytPlayerUtilsProvider;

  public MainActivity_MembersInjector(Provider<PlayerConnection> playerConnectionProvider,
      Provider<TokenManager> tokenManagerProvider, Provider<YTPlayerUtils> ytPlayerUtilsProvider) {
    this.playerConnectionProvider = playerConnectionProvider;
    this.tokenManagerProvider = tokenManagerProvider;
    this.ytPlayerUtilsProvider = ytPlayerUtilsProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<PlayerConnection> playerConnectionProvider,
      Provider<TokenManager> tokenManagerProvider, Provider<YTPlayerUtils> ytPlayerUtilsProvider) {
    return new MainActivity_MembersInjector(playerConnectionProvider, tokenManagerProvider, ytPlayerUtilsProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectPlayerConnection(instance, playerConnectionProvider.get());
    injectTokenManager(instance, tokenManagerProvider.get());
    injectYtPlayerUtils(instance, ytPlayerUtilsProvider.get());
  }

  @InjectedFieldSignature("com.appplayer.music.MainActivity.playerConnection")
  public static void injectPlayerConnection(MainActivity instance,
      PlayerConnection playerConnection) {
    instance.playerConnection = playerConnection;
  }

  @InjectedFieldSignature("com.appplayer.music.MainActivity.tokenManager")
  public static void injectTokenManager(MainActivity instance, TokenManager tokenManager) {
    instance.tokenManager = tokenManager;
  }

  @InjectedFieldSignature("com.appplayer.music.MainActivity.ytPlayerUtils")
  public static void injectYtPlayerUtils(MainActivity instance, YTPlayerUtils ytPlayerUtils) {
    instance.ytPlayerUtils = ytPlayerUtils;
  }
}
