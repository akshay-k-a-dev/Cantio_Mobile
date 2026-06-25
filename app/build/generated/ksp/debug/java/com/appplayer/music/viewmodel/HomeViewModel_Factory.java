package com.appplayer.music.viewmodel;

import com.appplayer.music.data.repository.MusicRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<MusicRepository> musicRepositoryProvider;

  public HomeViewModel_Factory(Provider<MusicRepository> musicRepositoryProvider) {
    this.musicRepositoryProvider = musicRepositoryProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(musicRepositoryProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<MusicRepository> musicRepositoryProvider) {
    return new HomeViewModel_Factory(musicRepositoryProvider);
  }

  public static HomeViewModel newInstance(MusicRepository musicRepository) {
    return new HomeViewModel(musicRepository);
  }
}
