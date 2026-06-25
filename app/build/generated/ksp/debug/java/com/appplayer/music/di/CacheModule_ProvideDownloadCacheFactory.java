package com.appplayer.music.di;

import android.content.Context;
import androidx.media3.datasource.cache.SimpleCache;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "com.appplayer.music.di.DownloadCache",
    "dagger.hilt.android.qualifiers.ApplicationContext"
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
public final class CacheModule_ProvideDownloadCacheFactory implements Factory<SimpleCache> {
  private final Provider<Context> contextProvider;

  public CacheModule_ProvideDownloadCacheFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SimpleCache get() {
    return provideDownloadCache(contextProvider.get());
  }

  public static CacheModule_ProvideDownloadCacheFactory create(Provider<Context> contextProvider) {
    return new CacheModule_ProvideDownloadCacheFactory(contextProvider);
  }

  public static SimpleCache provideDownloadCache(Context context) {
    return Preconditions.checkNotNullFromProvides(CacheModule.INSTANCE.provideDownloadCache(context));
  }
}
