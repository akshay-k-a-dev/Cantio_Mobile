package com.appplayer.music.playback;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PlayerConnection_Factory implements Factory<PlayerConnection> {
  private final Provider<Context> contextProvider;

  public PlayerConnection_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PlayerConnection get() {
    return newInstance(contextProvider.get());
  }

  public static PlayerConnection_Factory create(Provider<Context> contextProvider) {
    return new PlayerConnection_Factory(contextProvider);
  }

  public static PlayerConnection newInstance(Context context) {
    return new PlayerConnection(context);
  }
}
