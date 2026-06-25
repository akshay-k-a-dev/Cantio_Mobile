package com.appplayer.music.di;

import com.appplayer.innertube.InnerTube;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class NetworkModule_ProvideInnerTubeFactory implements Factory<InnerTube> {
  @Override
  public InnerTube get() {
    return provideInnerTube();
  }

  public static NetworkModule_ProvideInnerTubeFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InnerTube provideInnerTube() {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideInnerTube());
  }

  private static final class InstanceHolder {
    private static final NetworkModule_ProvideInnerTubeFactory INSTANCE = new NetworkModule_ProvideInnerTubeFactory();
  }
}
