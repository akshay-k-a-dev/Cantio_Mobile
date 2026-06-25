package com.appplayer.music.utils;

import com.appplayer.innertube.InnerTube;
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
public final class YTPlayerUtils_Factory implements Factory<YTPlayerUtils> {
  private final Provider<InnerTube> innerTubeProvider;

  public YTPlayerUtils_Factory(Provider<InnerTube> innerTubeProvider) {
    this.innerTubeProvider = innerTubeProvider;
  }

  @Override
  public YTPlayerUtils get() {
    return newInstance(innerTubeProvider.get());
  }

  public static YTPlayerUtils_Factory create(Provider<InnerTube> innerTubeProvider) {
    return new YTPlayerUtils_Factory(innerTubeProvider);
  }

  public static YTPlayerUtils newInstance(InnerTube innerTube) {
    return new YTPlayerUtils(innerTube);
  }
}
