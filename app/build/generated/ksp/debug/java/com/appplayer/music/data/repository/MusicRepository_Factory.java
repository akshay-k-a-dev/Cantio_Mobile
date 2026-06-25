package com.appplayer.music.data.repository;

import com.appplayer.music.data.api.CantioApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class MusicRepository_Factory implements Factory<MusicRepository> {
  private final Provider<CantioApiService> apiProvider;

  public MusicRepository_Factory(Provider<CantioApiService> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public MusicRepository get() {
    return newInstance(apiProvider.get());
  }

  public static MusicRepository_Factory create(Provider<CantioApiService> apiProvider) {
    return new MusicRepository_Factory(apiProvider);
  }

  public static MusicRepository newInstance(CantioApiService api) {
    return new MusicRepository(api);
  }
}
