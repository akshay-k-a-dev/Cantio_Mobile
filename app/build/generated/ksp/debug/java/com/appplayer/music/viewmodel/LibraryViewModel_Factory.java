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
public final class LibraryViewModel_Factory implements Factory<LibraryViewModel> {
  private final Provider<MusicRepository> musicRepositoryProvider;

  public LibraryViewModel_Factory(Provider<MusicRepository> musicRepositoryProvider) {
    this.musicRepositoryProvider = musicRepositoryProvider;
  }

  @Override
  public LibraryViewModel get() {
    return newInstance(musicRepositoryProvider.get());
  }

  public static LibraryViewModel_Factory create(Provider<MusicRepository> musicRepositoryProvider) {
    return new LibraryViewModel_Factory(musicRepositoryProvider);
  }

  public static LibraryViewModel newInstance(MusicRepository musicRepository) {
    return new LibraryViewModel(musicRepository);
  }
}
